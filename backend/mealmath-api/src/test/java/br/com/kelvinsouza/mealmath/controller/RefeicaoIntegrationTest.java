package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.repository.ItemMercadoRepository;
import br.com.kelvinsouza.mealmath.repository.RefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Testa o CRUD da biblioteca de refeicoes (RF003) junto com o calculo do custo (RF005). */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
@DisplayName("Refeições — biblioteca (RF003)")
class RefeicaoIntegrationTest {

    private static final String SENHA = "senha-forte-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ItemMercadoRepository itemMercadoRepository;
    @Autowired private RefeicaoRepository refeicaoRepository;
    @Autowired private RegistroDiarioRepository registroDiarioRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @PersistenceContext private EntityManager entityManager;

    private String tokenAna;
    private String tokenBruno;
    private long frangoId;
    private long aveiaId;

    @BeforeEach
    void prepararCenario() throws Exception {
        tokenAna = registrar("Ana", "ana@exemplo.com");
        tokenBruno = registrar("Bruno", "bruno@exemplo.com");

        frangoId = criarItemMercado(tokenAna, "Frango", "18.90", "1", "KG");
        aveiaId = criarItemMercado(tokenAna, "Aveia", "9.50", "500", "G");
    }

    private String registrar(String nome, String email) throws Exception {
        String corpo =
                mockMvc.perform(
                                post("/auth/registrar")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"nome":"%s","email":"%s","senha":"%s"}
                                                """
                                                        .formatted(nome, email, SENHA)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(corpo).get("token").asText();
    }

    private long criarItemMercado(
            String token, String nome, String preco, String quantidade, String unidade)
            throws Exception {
        String corpo =
                mockMvc.perform(
                                post("/itens-mercado")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"nome":"%s","preco":%s,"quantidadeEmbalagem":%s,"unidade":"%s"}
                                                """
                                                        .formatted(nome, preco, quantidade, unidade)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private static String linhaItem(long itemMercadoId, String quantidade, String unidade) {
        return """
                {"itemMercadoId":%d,"quantidadeConsumida":%s,"unidade":"%s"}
                """
                .formatted(itemMercadoId, quantidade, unidade);
    }

    private static String corpoRefeicao(String titulo, String icone, String... itens) {
        return """
                {"titulo":"%s","icone":"%s","itens":[%s]}
                """
                .formatted(titulo, icone, String.join(",", itens));
    }

    private String criarRefeicao(String token, String corpo) throws Exception {
        return mockMvc.perform(
                        post("/refeicoes")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long idDe(String corpoJson) {
        return objectMapper.readTree(corpoJson).get("id").asLong();
    }

    @Nested
    @DisplayName("Cadastro (RF003)")
    class Cadastro {

        @Test
        @DisplayName("Cria a refeição com ícone, título e itens, devolvendo o custo total")
        void criaRefeicaoComCustoCalculado() throws Exception {
            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Almoço",
                                                    "prato",
                                                    linhaItem(frangoId, "150", "G"),
                                                    linhaItem(aveiaId, "40", "G"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.titulo").value("Almoço"))
                    .andExpect(jsonPath("$.icone").value("prato"))
                    .andExpect(jsonPath("$.itens.length()").value(2))
                    // 2,835 do frango + 0,76 da aveia
                    .andExpect(jsonPath("$.custoTotal").value(3.595))
                    .andExpect(jsonPath("$.itens[0].nome").value("Frango"))
                    .andExpect(jsonPath("$.itens[0].custo").value(2.835))
                    .andExpect(jsonPath("$.itens[1].custo").value(0.76));
        }

        @Test
        @DisplayName("Consumo em unidade diferente da embalagem, na mesma grandeza, é aceito")
        void aceitaConsumoEmUnidadeDiferenteDaEmbalagem() throws Exception {
            long leiteId = criarItemMercado(tokenAna, "Leite", "5.20", "1", "L");

            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Café", "xicara", linhaItem(leiteId, "200", "ML"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.custoTotal").value(1.04));
        }

        @Test
        @DisplayName("Consumir em g um item vendido em L é 400 — rejeitado na validação")
        void grandezaIncompativelRetorna400() throws Exception {
            long leiteId = criarItemMercado(tokenAna, "Leite", "5.20", "1", "L");

            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Errada", "prato", linhaItem(leiteId, "200", "G"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(
                            org.hamcrest.Matchers.containsString("incompatíveis")));
        }

        @Test
        void quantidadeZeroRetorna400() throws Exception {
            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Zerada", "prato", linhaItem(frangoId, "0", "G"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['itens[0].quantidadeConsumida']").isNotEmpty());
        }

        @Test
        void refeicaoSemItensRetorna400() throws Exception {
            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"titulo":"Vazia","icone":"prato","itens":[]}
                                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.itens").isNotEmpty());
        }

        @Test
        void tituloEmBrancoRetorna400() throws Exception {
            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "", "prato", linhaItem(frangoId, "150", "G"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.titulo").isNotEmpty());
        }

        @Test
        @DisplayName("Item de mercado desativado não pode entrar em refeição nova")
        void itemDesativadoRetorna409() throws Exception {
            mockMvc.perform(
                            delete("/itens-mercado/" + frangoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Almoço", "prato", linhaItem(frangoId, "150", "G"))))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Isolamento multi-tenant")
    class Isolamento {

        @Test
        @DisplayName("Referenciar item de mercado de outro usuário é 404, não empresta o preço")
        void naoPermiteUsarItemDeOutroUsuario() throws Exception {
            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Roubada", "prato", linhaItem(frangoId, "150", "G"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void listagemSoTrazRefeicoesDoProprioUsuario() throws Exception {
            criarRefeicao(
                    tokenAna, corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G")));

            long itemBruno = criarItemMercado(tokenBruno, "Arroz", "5.00", "1", "KG");
            criarRefeicao(
                    tokenBruno, corpoRefeicao("Jantar", "prato", linhaItem(itemBruno, "100", "G")));

            mockMvc.perform(get("/refeicoes").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].titulo").value("Almoço"));

            mockMvc.perform(get("/refeicoes").header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].titulo").value("Jantar"));
        }

        @Test
        void buscarRefeicaoDeOutroUsuarioRetorna404() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            mockMvc.perform(get("/refeicoes/" + id).header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());
        }

        @Test
        void excluirRefeicaoDeOutroUsuarioRetorna404() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            mockMvc.perform(
                            delete("/refeicoes/" + id).header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());
        }

        @Test
        void semTokenRetorna401() throws Exception {
            mockMvc.perform(get("/refeicoes")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Edição")
    class Edicao {

        @Test
        @DisplayName("A lista de itens do PUT substitui a composição inteira")
        void atualizarSubstituiOsItens() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao(
                                    "Almoço",
                                    "prato",
                                    linhaItem(frangoId, "150", "G"),
                                    linhaItem(aveiaId, "40", "G"))));

            mockMvc.perform(
                            put("/refeicoes/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Almoço leve",
                                                    "salada",
                                                    linhaItem(aveiaId, "40", "G"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.titulo").value("Almoço leve"))
                    .andExpect(jsonPath("$.icone").value("salada"))
                    .andExpect(jsonPath("$.itens.length()").value(1))
                    .andExpect(jsonPath("$.custoTotal").value(0.76));
        }

        @Test
        @DisplayName("As linhas removidas somem do banco (orphanRemoval), não ficam órfãs")
        void itensRemovidosSaoApagados() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao(
                                    "Almoço",
                                    "prato",
                                    linhaItem(frangoId, "150", "G"),
                                    linhaItem(aveiaId, "40", "G"))));

            mockMvc.perform(
                            put("/refeicoes/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Almoço", "prato", linhaItem(aveiaId, "40", "G"))))
                    .andExpect(status().isOk());

            entityManager.flush();
            entityManager.clear();

            Long total =
                    entityManager
                            .createQuery(
                                    "select count(i) from ItemRefeicao i where i.refeicao.id = :id",
                                    Long.class)
                            .setParameter("id", id)
                            .getSingleResult();

            assertThat(total).isEqualTo(1L);
        }

        @Test
        @DisplayName("Alterar a quantidade recalcula o custo")
        void alterarQuantidadeRecalculaCusto() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            mockMvc.perform(
                            put("/refeicoes/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Almoço", "prato", linhaItem(frangoId, "300", "G"))))
                    .andExpect(jsonPath("$.custoTotal").value(5.67));
        }

        @Test
        @DisplayName("Item desativado que já estava na refeição não trava a edição")
        void itemJaVinculadoDesativadoPodePermanecer() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            mockMvc.perform(
                            delete("/itens-mercado/" + frangoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            put("/refeicoes/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            corpoRefeicao(
                                                    "Almoço reforçado",
                                                    "prato",
                                                    linhaItem(frangoId, "200", "G"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[0].itemAtivo").value(false))
                    .andExpect(jsonPath("$.custoTotal").value(3.78));
        }

        @Test
        @DisplayName("Atualizar o preço do item de mercado reflete no custo da refeição (RF007)")
        void precoNovoRefleteNoCustoDaRefeicao() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            mockMvc.perform(
                            put("/itens-mercado/" + frangoId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"nome":"Frango","preco":22.50,"quantidadeEmbalagem":1,"unidade":"KG"}
                                            """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/refeicoes/" + id).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.custoTotal").value(3.375));
        }
    }

    @Nested
    @DisplayName("Exclusão e separação Biblioteca/Diário")
    class Exclusao {

        @Test
        void excluirRemoveDaBiblioteca() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            mockMvc.perform(delete("/refeicoes/" + id).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/refeicoes/" + id).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Apagar o modelo não apaga o dia já registrado — só desfaz o vínculo de rastreio")
        void excluirRefeicaoPreservaORegistroDiario() throws Exception {
            long refeicaoId =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao("Almoço", "prato", linhaItem(frangoId, "150", "G"))));

            Usuario ana = usuarioRepository.findByEmailIgnoreCase("ana@exemplo.com").orElseThrow();
            ItemMercado frango =
                    itemMercadoRepository.findByIdAndUsuarioId(frangoId, ana.getId()).orElseThrow();
            Refeicao modelo =
                    refeicaoRepository.findByIdAndUsuarioId(refeicaoId, ana.getId()).orElseThrow();

            RegistroDiario dia =
                    new RegistroDiario(ana, LocalDate.of(2026, 8, 3), "Almoço", "prato", modelo);
            dia.adicionarItem(
                    new ItemRegistro(frango, "Frango", new BigDecimal("150"), UnidadeMedida.G));
            registroDiarioRepository.saveAndFlush(dia);
            Long registroId = dia.getId();

            mockMvc.perform(
                            delete("/refeicoes/" + refeicaoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            entityManager.flush();
            entityManager.clear();

            RegistroDiario preservado =
                    registroDiarioRepository.findByIdAndUsuarioId(registroId, ana.getId())
                            .orElseThrow();

            assertThat(preservado.getRefeicaoOrigem()).isNull();
            assertThat(preservado.getTitulo()).isEqualTo("Almoço");
            assertThat(preservado.getItens()).hasSize(1);
            assertThat(preservado.getItens().get(0).getQuantidadeConsumida())
                    .isEqualByComparingTo(new BigDecimal("150"));
        }
    }

    @Nested
    @DisplayName("N+1 na listagem")
    class PrevencaoNMaisUm {

        private Statistics estatisticas() {
            return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        }

        /**
         * Limpa o contexto de persistencia antes de contar as consultas. Sem isso as entidades
         * criadas no proprio teste ficariam no cache do Hibernate e o GET nao consultaria nada,
         * fazendo o teste passar sem provar nada.
         */
        private long consultasDaListagem(String token) throws Exception {
            entityManager.flush();
            entityManager.clear();

            Statistics stats = estatisticas();
            stats.clear();

            mockMvc.perform(get("/refeicoes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            return stats.getPrepareStatementCount();
        }

        @Test
        @DisplayName("A contagem de consultas não cresce com o número de refeições")
        void listagemNaoDisparaConsultaPorRefeicao() throws Exception {
            criarRefeicao(
                    tokenAna,
                    corpoRefeicao(
                            "Refeição 1",
                            "prato",
                            linhaItem(frangoId, "150", "G"),
                            linhaItem(aveiaId, "40", "G")));

            long comUmaRefeicao = consultasDaListagem(tokenAna);

            for (int i = 2; i <= 6; i++) {
                criarRefeicao(
                        tokenAna,
                        corpoRefeicao(
                                "Refeição " + i,
                                "prato",
                                linhaItem(frangoId, "150", "G"),
                                linhaItem(aveiaId, "40", "G")));
            }

            long comSeisRefeicoes = consultasDaListagem(tokenAna);

            assertThat(comSeisRefeicoes)
                    .as(
                            "1 refeição custou %d consultas e 6 custaram %d — o @EntityGraph deveria"
                                    + " manter o número constante",
                            comUmaRefeicao, comSeisRefeicoes)
                    .isEqualTo(comUmaRefeicao);
        }

        @Test
        @DisplayName("A listagem completa cabe em uma única consulta")
        void listagemUsaUmaConsultaSo() throws Exception {
            for (int i = 1; i <= 3; i++) {
                criarRefeicao(
                        tokenAna,
                        corpoRefeicao(
                                "Refeição " + i,
                                "prato",
                                linhaItem(frangoId, "150", "G"),
                                linhaItem(aveiaId, "40", "G")));
            }

            assertThat(consultasDaListagem(tokenAna)).isEqualTo(1L);
        }

        @Test
        @DisplayName("Buscar uma refeição por id também traz itens e preços em uma consulta")
        void buscaPorIdUsaUmaConsultaSo() throws Exception {
            long id =
                    idDe(criarRefeicao(
                            tokenAna,
                            corpoRefeicao(
                                    "Almoço",
                                    "prato",
                                    linhaItem(frangoId, "150", "G"),
                                    linhaItem(aveiaId, "40", "G"))));

            entityManager.flush();
            entityManager.clear();
            Statistics stats = estatisticas();
            stats.clear();

            mockMvc.perform(get("/refeicoes/" + id).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens.length()").value(2));

            assertThat(stats.getPrepareStatementCount()).isEqualTo(1L);
        }
    }
}
