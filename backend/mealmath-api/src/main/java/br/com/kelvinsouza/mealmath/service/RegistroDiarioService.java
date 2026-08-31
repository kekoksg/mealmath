package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import br.com.kelvinsouza.mealmath.domain.exception.RegraNegocioException;
import br.com.kelvinsouza.mealmath.dto.DuplicarDiaAnteriorRequest;
import br.com.kelvinsouza.mealmath.dto.ItemRegistroQuantidadeRequest;
import br.com.kelvinsouza.mealmath.dto.ItemRegistroResponse;
import br.com.kelvinsouza.mealmath.dto.RegistroDiarioRequest;
import br.com.kelvinsouza.mealmath.dto.RegistroDiarioResponse;
import br.com.kelvinsouza.mealmath.repository.RefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticadoProvider;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diario de consumo (RF009): o que foi realmente comido em cada dia. E a unica fonte do custo
 * consolidado do periodo (RF006).
 *
 * Em nenhum ponto daqui um ItemRefeicao vira item do diario. O que se copia sao os valores (nome,
 * quantidade e unidade) mais a referencia ao item de mercado, compartilhada de proposito por ser a
 * fonte do preco. E o que faz ajustar o almoco de hoje nao mexer no modelo nem no almoco de ontem.
 */
@Service
public class RegistroDiarioService {

    private final RegistroDiarioRepository registroDiarioRepository;
    private final RefeicaoRepository refeicaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalculadoraCustoService calculadora;
    private final ConversorUnidadeService conversor;
    private final UsuarioAutenticadoProvider usuarioAutenticado;

    public RegistroDiarioService(
            RegistroDiarioRepository registroDiarioRepository,
            RefeicaoRepository refeicaoRepository,
            UsuarioRepository usuarioRepository,
            CalculadoraCustoService calculadora,
            ConversorUnidadeService conversor,
            UsuarioAutenticadoProvider usuarioAutenticado) {
        this.registroDiarioRepository = registroDiarioRepository;
        this.refeicaoRepository = refeicaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.calculadora = calculadora;
        this.conversor = conversor;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    /** Joga uma refeicao da biblioteca em uma data, copiando os itens dela (RF009). */
    @Transactional
    public RegistroDiarioResponse registrar(RegistroDiarioRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();

        Refeicao modelo =
                refeicaoRepository
                        .findByIdAndUsuarioId(requisicao.refeicaoId(), usuarioId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Refeição"));

        Usuario dono = usuarioRepository.getReferenceById(usuarioId);
        RegistroDiario registro = copiarDaBiblioteca(dono, requisicao.data(), modelo);

        return montarResposta(registroDiarioRepository.save(registro));
    }

    /**
     * Busca os registros de um dia so ou de um intervalo. As duas consultas usam @EntityGraph com
     * os itens e os itens de mercado, entao calcular o custo de N registros nao dispara uma
     * consulta para cada um.
     */
    @Transactional(readOnly = true)
    public List<RegistroDiarioResponse> listar(LocalDate data, LocalDate inicio, LocalDate fim) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();

        if (data != null) {
            if (inicio != null || fim != null) {
                throw new RegraNegocioException(
                        "Informe apenas 'data' para um dia, ou 'inicio' e 'fim' para um intervalo.");
            }
            return mapear(registroDiarioRepository.findByUsuarioIdAndDataOrderByIdAsc(usuarioId, data));
        }

        if (inicio == null || fim == null) {
            throw new RegraNegocioException(
                    "Informe 'data' para um dia, ou 'inicio' e 'fim' para um intervalo.");
        }
        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("'inicio' não pode ser posterior a 'fim'.");
        }

        return mapear(
                registroDiarioRepository.findByUsuarioIdAndDataBetweenOrderByDataAscIdAsc(
                        usuarioId, inicio, fim));
    }

    @Transactional(readOnly = true)
    public RegistroDiarioResponse buscar(Long id) {
        return montarResposta(buscarDoUsuario(id));
    }

    /**
     * Muda a quantidade de um item so nesse registro (RF009).
     *
     * Procuro o item dentro do registro que ja foi carregado. Isso garante que ele e mesmo desse
     * registro e ainda evita uma segunda consulta no banco.
     */
    @Transactional
    public RegistroDiarioResponse ajustarItem(
            Long registroId, Long itemId, ItemRegistroQuantidadeRequest requisicao) {

        RegistroDiario registro = buscarDoUsuario(registroId);

        ItemRegistro item =
                registro.getItens().stream()
                        .filter(linha -> linha.getId().equals(itemId))
                        .findFirst()
                        .orElseThrow(
                                () -> new RecursoNaoEncontradoException("Item do registro diário"));

        // Sem vinculo de preco nao tem embalagem para comparar a grandeza. Mesmo assim a quantidade
        // continua editavel, para o usuario conseguir arrumar o registro.
        if (item.getItemMercado() != null) {
            conversor.exigirMesmaGrandeza(item.getItemMercado().getUnidade(), requisicao.unidade());
        }

        item.setQuantidadeConsumida(requisicao.quantidadeConsumida());
        item.setUnidade(requisicao.unidade());

        return montarResposta(registro);
    }

    /** Tira o registro do dia. Nao mexe na biblioteca, o modelo continua la. */
    @Transactional
    public void remover(Long id) {
        registroDiarioRepository.delete(buscarDoUsuario(id));
    }

    /**
     * Repete no dia informado tudo o que foi registrado no dia anterior (RF009).
     *
     * Os registros sao copiados do jeito que ficaram ontem, ja com os ajustes de quantidade que o
     * usuario fez. Repetir o dia e repetir o que foi comido e nao o que o modelo diz. Cada linha
     * vira um ItemRegistro novo: compartilhando a referencia, editar um dia mudaria o outro.
     */
    @Transactional
    public List<RegistroDiarioResponse> duplicarDiaAnterior(DuplicarDiaAnteriorRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        LocalDate destino = requisicao.data();
        LocalDate origem = destino.minusDays(1);

        List<RegistroDiario> registrosDeOntem =
                registroDiarioRepository.findByUsuarioIdAndDataOrderByIdAsc(usuarioId, origem);

        if (registrosDeOntem.isEmpty()) {
            throw new RegraNegocioException(
                    "Não há refeições registradas em %s para repetir.".formatted(origem));
        }

        Usuario dono = usuarioRepository.getReferenceById(usuarioId);
        List<RegistroDiario> copias =
                registrosDeOntem.stream()
                        .map(anterior -> copiarRegistro(dono, destino, anterior))
                        .toList();

        return mapear(registroDiarioRepository.saveAll(copias));
    }

    private RegistroDiario copiarDaBiblioteca(Usuario dono, LocalDate data, Refeicao modelo) {
        RegistroDiario registro =
                new RegistroDiario(dono, data, modelo.getTitulo(), modelo.getIcone(), modelo);

        for (ItemRefeicao linha : modelo.getItens()) {
            // Linha nova. O ItemMercado e compartilhado de proposito, porque e dele que sai o
            // preco e e o que faz a atualizacao de preco (RF007) chegar nos dias ja registrados.
            registro.adicionarItem(
                    new ItemRegistro(
                            linha.getItemMercado(),
                            linha.getItemMercado().getNome(),
                            linha.getQuantidadeConsumida(),
                            linha.getUnidade()));
        }

        return registro;
    }

    private RegistroDiario copiarRegistro(Usuario dono, LocalDate data, RegistroDiario origem) {
        RegistroDiario copia =
                new RegistroDiario(
                        dono,
                        data,
                        origem.getTitulo(),
                        origem.getIcone(),
                        origem.getRefeicaoOrigem());

        for (ItemRegistro linha : origem.getItens()) {
            copia.adicionarItem(
                    new ItemRegistro(
                            linha.getItemMercado(),
                            linha.getNomeItem(),
                            linha.getQuantidadeConsumida(),
                            linha.getUnidade()));
        }

        return copia;
    }

    private RegistroDiario buscarDoUsuario(Long id) {
        return registroDiarioRepository
                .findByIdAndUsuarioId(id, usuarioAutenticado.idDoUsuarioAutenticado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro diário"));
    }

    private List<RegistroDiarioResponse> mapear(List<RegistroDiario> registros) {
        return registros.stream().map(this::montarResposta).toList();
    }

    private RegistroDiarioResponse montarResposta(RegistroDiario registro) {
        List<ItemRegistroResponse> itens =
                registro.getItens().stream()
                        .map(
                                item ->
                                        ItemRegistroResponse.de(
                                                item,
                                                item.getItemMercado() == null
                                                        ? null
                                                        : calculadora.custoItem(item)))
                        .toList();

        ResultadoCusto resultado = calculadora.calcularRegistro(registro);

        return new RegistroDiarioResponse(
                registro.getId(),
                registro.getData(),
                registro.getTitulo(),
                registro.getIcone(),
                registro.getRefeicaoOrigem() == null ? null : registro.getRefeicaoOrigem().getId(),
                itens,
                resultado.total(),
                resultado.itensSemPreco());
    }
}
