package br.com.kelvinsouza.mealmath.config;

import br.com.kelvinsouza.mealmath.domain.Categoria;
import br.com.kelvinsouza.mealmath.domain.HistoricoPreco;
import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.MetaOrcamento;
import br.com.kelvinsouza.mealmath.domain.PeriodoMeta;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.repository.HistoricoPrecoRepository;
import br.com.kelvinsouza.mealmath.repository.ItemMercadoRepository;
import br.com.kelvinsouza.mealmath.repository.MetaOrcamentoRepository;
import br.com.kelvinsouza.mealmath.repository.RefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial de dados para desenvolvimento, reproduzindo no banco o que o prototipo de
 * referencia tem em memoria: usuario, 12 itens de mercado com historico, 4 refeicoes modelo,
 * duas semanas de diario e a meta.
 *
 * O dashboard (RF006) so mostra alguma coisa com historico acumulado, e cadastrar duas semanas na
 * mao a cada vez que o banco e recriado inviabiliza o desenvolvimento do front.
 *
 * Para rodar: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 *
 * Grava pelos repositories e nao pelos Services, porque os Services pegam o dono do token e aqui
 * nao existe requisicao. Em troca, preenche o usuario em todas as entidades na mao.
 *
 * As datas sao relativas a hoje, senao o dashboard apareceria vazio depois de alguns dias. E
 * idempotente pelo e-mail.
 */
@Component
@Profile("dev")
public class SeedDesenvolvimento implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDesenvolvimento.class);

    private static final String NOME = "Maria da Silva";
    private static final String EMAIL = "maria@email.com";

    /** Precisa de 8 caracteres por causa da validacao do cadastro (RF001). So vale no perfil dev. */
    private static final String SENHA = "senha123";

    private static final BigDecimal META_MENSAL = new BigDecimal("250.00");

        /**
         * Catalogo do mercado (RF004), na mesma ordem em que a BIBLIOTECA referencia por indice.
         *
         * Ha embalagens quebradas de proposito, como a aveia de 0,5 kg, para o front nao nascer
         * assumindo que toda compra e em kg redondo.
         */
    private static final List<ItemSeed> MERCADO =
            List.of(
                    new ItemSeed("Peito de frango", Categoria.PROTEINA, "18.90", "1", UnidadeMedida.KG, "16.50"),
                    new ItemSeed("Ovos (dúzia)", Categoria.PROTEINA, "12.00", "12", UnidadeMedida.UN, "11.20"),
                    new ItemSeed("Arroz integral", Categoria.CARBOIDRATO, "7.20", "1", UnidadeMedida.KG, null),
                    new ItemSeed("Feijão carioca", Categoria.CARBOIDRATO, "8.90", "1", UnidadeMedida.KG, "8.50"),
                    new ItemSeed("Aveia em flocos", Categoria.CARBOIDRATO, "9.50", "0.5", UnidadeMedida.KG, null),
                    new ItemSeed("Banana", Categoria.HORTIFRUTI, "5.40", "1", UnidadeMedida.KG, "4.20"),
                    new ItemSeed("Tomate", Categoria.HORTIFRUTI, "6.80", "1", UnidadeMedida.KG, null),
                    new ItemSeed("Brócolis", Categoria.HORTIFRUTI, "4.50", "1", UnidadeMedida.UN, null),
                    new ItemSeed("Leite", Categoria.LATICINIO, "5.20", "1", UnidadeMedida.L, "4.90"),
                    new ItemSeed("Iogurte natural", Categoria.LATICINIO, "7.90", "0.5", UnidadeMedida.L, null),
                    new ItemSeed("Azeite", Categoria.OUTROS, "32.00", "0.5", UnidadeMedida.L, "27.00"),
                    new ItemSeed("Pão integral", Categoria.CARBOIDRATO, "8.40", "0.4", UnidadeMedida.KG, null));

        /**
         * Biblioteca de refeicoes modelo (RF003), na ordem que o PLANO_DO_DIA usa.
         *
         * As quantidades estao em unidade diferente da embalagem de proposito (compra em KG e consumo
         * em G). O brocolis em 0,5 un cobre a fracao de unidade, que nao pode ser arredondada.
         */
    private static final List<RefeicaoSeed> BIBLIOTECA =
            List.of(
                    new RefeicaoSeed(
                            "ref-manha",
                            "Café da manhã",
                            List.of(
                                    new LinhaSeed(8, "200", UnidadeMedida.ML),
                                    new LinhaSeed(4, "40", UnidadeMedida.G),
                                    new LinhaSeed(5, "120", UnidadeMedida.G),
                                    new LinhaSeed(11, "50", UnidadeMedida.G))),
                    new RefeicaoSeed(
                            "ref-almoco",
                            "Almoço",
                            List.of(
                                    new LinhaSeed(0, "150", UnidadeMedida.G),
                                    new LinhaSeed(2, "100", UnidadeMedida.G),
                                    new LinhaSeed(3, "80", UnidadeMedida.G),
                                    new LinhaSeed(7, "0.5", UnidadeMedida.UN),
                                    new LinhaSeed(10, "8", UnidadeMedida.ML))),
                    new RefeicaoSeed(
                            "ref-salada",
                            "Lanche da Tarde",
                            List.of(
                                    new LinhaSeed(9, "170", UnidadeMedida.ML),
                                    new LinhaSeed(5, "120", UnidadeMedida.G))),
                    new RefeicaoSeed(
                            "ref-jantar",
                            "Jantar",
                            List.of(
                                    new LinhaSeed(1, "2", UnidadeMedida.UN),
                                    new LinhaSeed(11, "60", UnidadeMedida.G),
                                    new LinhaSeed(6, "100", UnidadeMedida.G))));

        /**
         * O que foi consumido em cada dia, pelo indice da BIBLIOTECA. A posicao no array e quantos dias
         * atras: 0 e hoje e 13 e treze dias atras.
         *
         * Os dias incompletos sao intencionais: pular o lanche ou o almoco e o que faz a media diaria e
         * a composicao mudarem de uma semana para a outra, em vez de dar sempre o mesmo numero.
         */
    private static final int[][] PLANO_DO_DIA = {
        {0, 1, 2, 3}, {0, 1, 3}, {0, 1, 2, 3}, {0, 2, 3}, {0, 1, 3}, {0, 1, 2, 3}, {0, 1, 2},
        {0, 1, 2, 3}, {0, 1, 3}, {0, 1, 2, 3}, {0, 3}, {0, 1, 2, 3}, {0, 1, 2, 3}, {0, 1, 3}
    };

    private final UsuarioRepository usuarioRepository;
    private final ItemMercadoRepository itemMercadoRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final RefeicaoRepository refeicaoRepository;
    private final RegistroDiarioRepository registroDiarioRepository;
    private final MetaOrcamentoRepository metaOrcamentoRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDesenvolvimento(
            UsuarioRepository usuarioRepository,
            ItemMercadoRepository itemMercadoRepository,
            HistoricoPrecoRepository historicoPrecoRepository,
            RefeicaoRepository refeicaoRepository,
            RegistroDiarioRepository registroDiarioRepository,
            MetaOrcamentoRepository metaOrcamentoRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.itemMercadoRepository = itemMercadoRepository;
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.refeicaoRepository = refeicaoRepository;
        this.registroDiarioRepository = registroDiarioRepository;
        this.metaOrcamentoRepository = metaOrcamentoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByEmailIgnoreCase(EMAIL)) {
            log.info("Seed de desenvolvimento ignorado: {} já está cadastrado.", EMAIL);
            return;
        }

        Usuario usuario =
                usuarioRepository.save(new Usuario(NOME, EMAIL, passwordEncoder.encode(SENHA)));

        List<ItemMercado> itens = criarItensDeMercado(usuario);
        List<Refeicao> refeicoes = criarBiblioteca(usuario, itens);
        int registros = criarDiario(usuario, refeicoes);

        metaOrcamentoRepository.save(new MetaOrcamento(usuario, PeriodoMeta.MENSAL, META_MENSAL));

        log.info(
                "Seed de desenvolvimento concluído — login {} / {} · {} itens de mercado, "
                        + "{} refeições, {} registros em {} dias, meta mensal de R$ {}.",
                EMAIL,
                SENHA,
                itens.size(),
                refeicoes.size(),
                registros,
                PLANO_DO_DIA.length,
                META_MENSAL);
    }

        /**
         * Cadastra o catalogo (RF004) e, nos itens reajustados, salva o preco antigo no HistoricoPreco
         * (RF007).
         *
         * A ordem importa: o item nasce com o preco novo e o historico recebe o antigo. E o formato que
         * o ItemMercadoService.atualizar gera, e e o que faz o dashboard enxergar a alta.
         */
    private List<ItemMercado> criarItensDeMercado(Usuario usuario) {
        List<ItemMercado> salvos = new ArrayList<>(MERCADO.size());

        for (ItemSeed seed : MERCADO) {
            ItemMercado item =
                    itemMercadoRepository.save(
                            new ItemMercado(
                                    usuario,
                                    seed.nome(),
                                    new BigDecimal(seed.preco()),
                                    new BigDecimal(seed.quantidadeEmbalagem()),
                                    seed.unidade(),
                                    seed.categoria()));

            if (seed.precoAnterior() != null) {
                historicoPrecoRepository.save(
                        new HistoricoPreco(
                                item,
                                new BigDecimal(seed.precoAnterior()),
                                item.getQuantidadeEmbalagem(),
                                item.getUnidade()));
            }

            salvos.add(item);
        }

        return salvos;
    }

    /** Refeicoes modelo da biblioteca (RF003). Elas nunca sao somadas no dashboard. */
    private List<Refeicao> criarBiblioteca(Usuario usuario, List<ItemMercado> itens) {
        List<Refeicao> salvas = new ArrayList<>(BIBLIOTECA.size());

        for (RefeicaoSeed seed : BIBLIOTECA) {
            Refeicao refeicao = new Refeicao(usuario, seed.titulo(), seed.icone());
            for (LinhaSeed linha : seed.itens()) {
                refeicao.adicionarItem(
                        new ItemRefeicao(
                                itens.get(linha.itemMercado()),
                                new BigDecimal(linha.quantidadeConsumida()),
                                linha.unidade()));
            }
            salvas.add(refeicaoRepository.save(refeicao));
        }

        return salvas;
    }

        /**
         * Cria no diario o que esta no PLANO_DO_DIA (RF009), de hoje para tras, e devolve quantos
         * registros criou.
         *
         * Cada registro recebe copia do titulo, do icone e dos itens, nunca as linhas da biblioteca.
         */
    private int criarDiario(Usuario usuario, List<Refeicao> refeicoes) {
        LocalDate hoje = LocalDate.now();
        int criados = 0;

        for (int diasAtras = 0; diasAtras < PLANO_DO_DIA.length; diasAtras++) {
            LocalDate data = hoje.minusDays(diasAtras);

            for (int indiceRefeicao : PLANO_DO_DIA[diasAtras]) {
                Refeicao modelo = refeicoes.get(indiceRefeicao);
                RegistroDiario registro =
                        new RegistroDiario(
                                usuario, data, modelo.getTitulo(), modelo.getIcone(), modelo);

                for (ItemRefeicao itemModelo : modelo.getItens()) {
                    registro.adicionarItem(
                            new ItemRegistro(
                                    itemModelo.getItemMercado(),
                                    itemModelo.getItemMercado().getNome(),
                                    itemModelo.getQuantidadeConsumida(),
                                    itemModelo.getUnidade()));
                }

                registroDiarioRepository.save(registro);
                criados++;
            }
        }

        return criados;
    }

    /**
     * Uma linha do catalogo de mercado. O precoAnterior e o valor que valia antes do ultimo
     * reajuste, ou nulo para item que nunca mudou de preco, porque sem dois valores nao existe
     * variacao para o dashboard alertar.
     */
    private record ItemSeed(
            String nome,
            Categoria categoria,
            String preco,
            String quantidadeEmbalagem,
            UnidadeMedida unidade,
            String precoAnterior) {}

    /** Um item dentro de uma refeicao modelo. O campo itemMercado e o indice na lista MERCADO. */
    private record LinhaSeed(int itemMercado, String quantidadeConsumida, UnidadeMedida unidade) {}

    private record RefeicaoSeed(String icone, String titulo, List<LinhaSeed> itens) {}
}
