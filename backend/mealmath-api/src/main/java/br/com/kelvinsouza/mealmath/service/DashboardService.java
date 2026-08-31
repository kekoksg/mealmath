package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.Categoria;
import br.com.kelvinsouza.mealmath.domain.HistoricoPreco;
import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.MetaOrcamento;
import br.com.kelvinsouza.mealmath.domain.PeriodoDashboard;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.dto.AltaPrecoResponse;
import br.com.kelvinsouza.mealmath.dto.ComparativoPeriodoResponse;
import br.com.kelvinsouza.mealmath.dto.CompletudeDiarioResponse;
import br.com.kelvinsouza.mealmath.dto.ComposicaoCategoriaResponse;
import br.com.kelvinsouza.mealmath.dto.DashboardResponse;
import br.com.kelvinsouza.mealmath.dto.ItemImpactoResponse;
import br.com.kelvinsouza.mealmath.dto.ProgressoMetaResponse;
import br.com.kelvinsouza.mealmath.repository.HistoricoPrecoRepository;
import br.com.kelvinsouza.mealmath.repository.MetaOrcamentoRepository;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticadoProvider;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta os numeros da tela de dashboard por periodo (RF006), sempre a partir do Diario. */
@Service
public class DashboardService {

    // Mesma precisao da CalculadoraCustoService, so para as divisoes nao estourarem excecao.
    private static final MathContext PRECISAO_DIVISAO = MathContext.DECIMAL128;

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_PERCENTUAL = 2;

    // O cartao de maior impacto e um ranking, nao um extrato. Cinco itens ja respondem "o que
    // puxa o gasto para cima" e sobra folga para o front mostrar menos.
    private static final int LIMITE_ITENS_IMPACTO = 5;

    // Tres alertas de alta que o usuario le valem mais que quinze que ele ignora.
    private static final int LIMITE_ALTAS_PRECO = 3;

    private final RegistroDiarioRepository registroDiarioRepository;
    private final MetaOrcamentoRepository metaOrcamentoRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final CalculadoraCustoService calculadora;
    private final UsuarioAutenticadoProvider usuarioAutenticado;

    public DashboardService(
            RegistroDiarioRepository registroDiarioRepository,
            MetaOrcamentoRepository metaOrcamentoRepository,
            HistoricoPrecoRepository historicoPrecoRepository,
            CalculadoraCustoService calculadora,
            UsuarioAutenticadoProvider usuarioAutenticado) {
        this.registroDiarioRepository = registroDiarioRepository;
        this.metaOrcamentoRepository = metaOrcamentoRepository;
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.calculadora = calculadora;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    /**
     * Monta o dashboard da janela pedida, terminando hoje.
     *
     * LocalDate.now, sem hora e sem fuso, para a virada do dia ser a mesma no servidor e no
     * navegador do usuario.
     */
    @Transactional(readOnly = true)
    public DashboardResponse consolidar(PeriodoDashboard periodo) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        int dias = periodo.getDias();

        // Duas janelas grudadas e do mesmo tamanho: a atual e a de tras. O "-1" das duas e o que
        // faz as pontas serem inclusivas. Sem ele, o periodo DIA pegaria dois dias.
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = inicioDaJanela(periodo, hoje);
        LocalDate fim = inicio.plusDays(dias - 1L);
        LocalDate inicioAnterior = inicio.minusDays(dias);
        LocalDate fimAnterior = inicio.minusDays(1);

        Agregado atual = agregar(buscarRegistros(usuarioId, inicio, fim));
        Agregado anterior = agregar(buscarRegistros(usuarioId, inicioAnterior, fimAnterior));

        long diasComRegistro =
                registroDiarioRepository.contarDiasComRegistro(usuarioId, inicio, fim);

        Optional<MetaOrcamento> meta = metaOrcamentoRepository.findByUsuarioId(usuarioId);

        return new DashboardResponse(
                periodo,
                inicio,
                fim,
                atual.total(),
                // Divide pelos dias da janela e nao pelos dias que tem registro.
                atual.total().divide(BigDecimal.valueOf(dias), PRECISAO_DIVISAO),
                comparar(atual.total(), anterior.total(), inicioAnterior, fimAnterior),
                new CompletudeDiarioResponse(diasComRegistro, dias),
                meta.map(definida -> progresso(definida, atual.total(), dias)).orElse(null),
                composicao(atual),
                itensMaiorImpacto(atual),
                altasDePreco(usuarioId),
                atual.itensSemPreco());
    }

        /**
         * Primeiro dia da janela, conforme o recorte de cada {@link PeriodoDashboard}.
         *
         * A janela de SEMANA vai ate sabado, entao inclui dias que ainda nao aconteceram. Nao ha
         * registro no futuro e o total nao muda, mas o custo medio divide pelos sete dias e sai menor
         * durante a semana corrente. E o preco de o indicador poder dizer "n/7".
         */
    private LocalDate inicioDaJanela(PeriodoDashboard periodo, LocalDate hoje) {
        if (periodo == PeriodoDashboard.SEMANA) {
            return hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        }
        return hoje.minusDays(periodo.getDias() - 1L);
    }

    private List<RegistroDiario> buscarRegistros(Long usuarioId, LocalDate inicio, LocalDate fim) {
        // O @EntityGraph do repository traz os itens e os itens de mercado junto. Sem isso cada
        // registro dispararia uma consulta nova (problema do N+1).
        return registroDiarioRepository.findByUsuarioIdAndDataBetweenOrderByDataAscIdAsc(
                usuarioId, inicio, fim);
    }

    /**
     * Passa pelo consumo do periodo uma vez so, juntando total, categorias e itens.
     *
     * A soma e item por item com a escala cheia, sem arredondar antes, senao o total sai distorcido.
     * Item sem item de mercado vinculado fica de fora do total e volta na lista de pendencias, em
     * vez de contar como R$ 0,00.
     */
    private Agregado agregar(List<RegistroDiario> registros) {
        BigDecimal total = BigDecimal.ZERO;
        Map<Categoria, BigDecimal> porCategoria = new EnumMap<>(Categoria.class);
        Map<Long, ImpactoItem> porItem = new LinkedHashMap<>();
        Set<String> semPreco = new LinkedHashSet<>();

        for (RegistroDiario registro : registros) {
            for (ItemRegistro item : registro.getItens()) {
                ItemMercado mercado = item.getItemMercado();
                if (mercado == null) {
                    // Set porque o mesmo alimento faltando em cinco dias e um aviso so.
                    semPreco.add(item.getDescricao());
                    continue;
                }

                BigDecimal custo = calculadora.custoItem(item);
                total = total.add(custo);
                porCategoria.merge(mercado.getCategoria(), custo, BigDecimal::add);
                porItem.computeIfAbsent(mercado.getId(), id -> new ImpactoItem(mercado))
                        .acumular(custo);
            }
        }

        return new Agregado(total, porCategoria, porItem, List.copyOf(semPreco));
    }

    /**
     * Variacao em relacao ao periodo anterior, ou null quando nao tem com o que comparar.
     *
     * Null porque sem gasto anterior a divisao nao tem denominador, e mostrar "+100%" na
     * primeira semana de uso seria inventar um numero. Isso tambem cobre o caso do periodo anterior
     * ter registros que somam zero por falta de preco.
     */
    private ComparativoPeriodoResponse comparar(
            BigDecimal totalAtual, BigDecimal totalAnterior, LocalDate inicio, LocalDate fim) {

        if (totalAnterior.signum() == 0) {
            return null;
        }

        BigDecimal variacao =
                totalAtual
                        .subtract(totalAnterior)
                        .divide(totalAnterior, PRECISAO_DIVISAO)
                        .multiply(CEM)
                        .setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);

        return new ComparativoPeriodoResponse(inicio, fim, totalAnterior, variacao);
    }

        /**
         * Passa pela taxa diaria (valor / dias do periodo da meta) e volta multiplicando pelos dias da
         * janela, porque a meta e o dashboard tem periodos independentes.
         *
         * Sem risco de divisao por zero: o valor e @Positive na entidade e os dias vem do enum.
         */
    private ProgressoMetaResponse progresso(MetaOrcamento meta, BigDecimal custoTotal, int dias) {
        BigDecimal diaria =
                meta.getValor()
                        .divide(BigDecimal.valueOf(meta.getPeriodo().getDias()), PRECISAO_DIVISAO);
        BigDecimal valorNoPeriodo = diaria.multiply(BigDecimal.valueOf(dias));

        BigDecimal percentual =
                custoTotal
                        .divide(valorNoPeriodo, PRECISAO_DIVISAO)
                        .multiply(CEM)
                        .setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);

        BigDecimal saldo = valorNoPeriodo.subtract(custoTotal);

        return new ProgressoMetaResponse(
                meta.getValor(),
                meta.getPeriodo(),
                valorNoPeriodo,
                percentual,
                saldo,
                saldo.signum() < 0);
    }

    /** Fatias do grafico de composicao, da maior para a menor. Vem vazia quando nao teve gasto. */
    private List<ComposicaoCategoriaResponse> composicao(Agregado agregado) {
        return agregado.porCategoria().entrySet().stream()
                .sorted(Map.Entry.<Categoria, BigDecimal>comparingByValue().reversed())
                .map(
                        fatia ->
                                new ComposicaoCategoriaResponse(
                                        fatia.getKey(),
                                        fatia.getKey().getRotulo(),
                                        fatia.getValue(),
                                        participacao(fatia.getValue(), agregado.total())))
                .toList();
    }

    /** Ranking dos itens que mais pesaram no gasto, do maior para o menor. */
    private List<ItemImpactoResponse> itensMaiorImpacto(Agregado agregado) {
        return agregado.porItem().values().stream()
                .sorted(Comparator.comparing(ImpactoItem::custo).reversed())
                .limit(LIMITE_ITENS_IMPACTO)
                .map(
                        impacto ->
                                new ItemImpactoResponse(
                                        impacto.item().getId(),
                                        impacto.item().getNome(),
                                        impacto.item().getCategoria(),
                                        impacto.custo(),
                                        participacao(impacto.custo(), agregado.total())))
                .toList();
    }

        /**
         * Itens cujo custo unitario subiu na ultima troca de preco (RF007), do maior aumento para o
         * menor.
         *
         * O preco mais novo esta no proprio ItemMercado e o anterior e a ultima linha do HistoricoPreco,
         * entao comparar os dois responde "o que subiu desde a ultima compra". Por isso o alerta nao
         * filtra pela janela consultada.
         */
    private List<AltaPrecoResponse> altasDePreco(Long usuarioId) {
        List<AltaPrecoResponse> altas = new ArrayList<>();

        for (HistoricoPreco anterior : historicoPrecoRepository.buscarUltimaTrocaDeCadaItem(usuarioId)) {
            ItemMercado item = anterior.getItemMercado();

            BigDecimal custoAnterior =
                    calculadora.custoUnitarioDe(
                            anterior.getPreco(),
                            anterior.getQuantidadeEmbalagem(),
                            anterior.getUnidade());
            BigDecimal custoAtual = calculadora.custoUnitario(item);

            // So interessa alta. Queda de preco nao e alerta e variacao zero e ruido. Isso
            // tambem descarta a alteracao que mexeu so no nome ou na categoria do item.
            if (custoAtual.compareTo(custoAnterior) <= 0) {
                continue;
            }

            BigDecimal variacao =
                    custoAtual
                            .subtract(custoAnterior)
                            .divide(custoAnterior, PRECISAO_DIVISAO)
                            .multiply(CEM)
                            .setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);

            altas.add(
                    new AltaPrecoResponse(
                            item.getId(),
                            item.getNome(),
                            anterior.getPreco(),
                            item.getPreco(),
                            custoAnterior,
                            custoAtual,
                            item.getUnidade().getGrandeza().getUnidadeBase(),
                            variacao,
                            anterior.getSubstituidoEm()));
        }

        return altas.stream()
                .sorted(Comparator.comparing(AltaPrecoResponse::variacaoPercentual).reversed())
                .limit(LIMITE_ALTAS_PRECO)
                .toList();
    }

    /**
     * Quanto uma parte representa do total, em porcentagem com 2 casas.
     *
     * O if de total zero na pratica nunca acontece, porque nao existe parte sem total, mas fica
     * porque e uma linha e sem ele o risco e uma ArithmeticException.
     */
    private BigDecimal participacao(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(ESCALA_PERCENTUAL);
        }
        return parte.divide(total, PRECISAO_DIVISAO)
                .multiply(CEM)
                .setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
    }

    /** Guarda o resultado de uma passada pelo consumo do periodo. */
    private record Agregado(
            BigDecimal total,
            Map<Categoria, BigDecimal> porCategoria,
            Map<Long, ImpactoItem> porItem,
            List<String> itensSemPreco) {}

    /**
     * Vai somando o custo de um item ao longo do periodo. Mutavel porque so vive dentro do
     * metodo, e criar um record novo a cada linha do diario so para somar seria desperdicio.
     */
    private static final class ImpactoItem {

        private final ItemMercado item;
        private BigDecimal custo = BigDecimal.ZERO;

        private ImpactoItem(ItemMercado item) {
            this.item = item;
        }

        private void acumular(BigDecimal valor) {
            custo = custo.add(valor);
        }

        private ItemMercado item() {
            return item;
        }

        private BigDecimal custo() {
            return custo;
        }
    }
}
