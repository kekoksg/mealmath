package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
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

/** Testa o diario de consumo (RF008) e a separacao entre a biblioteca e o diario. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
@DisplayName("Diário — registro de consumo (RF008)")
class RegistroDiarioIntegrationTest {

    private static final String SENHA = "senha-forte-123";
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 3);
    private static final LocalDate ONTEM = HOJE.minusDays(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RegistroDiarioRepository registroDiarioRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @PersistenceContext private EntityManager entityManager;

    private String tokenAna;
    private String tokenBruno;
    private long frangoId;
    private long aveiaId;
    private long almocoId;

    @BeforeEach
    void prepararCenario() throws Exception {
        tokenAna = registrar("Ana", "ana@exemplo.com");
        tokenBruno = registrar("Bruno", "bruno@exemplo.com");

        frangoId = criarItemMercado(tokenAna, "Frango", "18.90", "1", "KG");
        aveiaId = criarItemMercado(tokenAna, "Aveia", "9.50", "500", "G");

        almocoId =
                idDe(
                        criarRefeicao(
                                tokenAna,
                                """
                                {"titulo":"Almoço","icone":"prato","itens":[
                                  {"itemMercadoId":%d,"quantidadeConsumida":150,"unidade":"G"},
                                  {"itemMercadoId":%d,"quantidadeConsumida":40,"unidade":"G"}]}
                                """
                                        .formatted(frangoId, aveiaId)));
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

    /** O sub do JWT e o id do usuario, ver o TokenService. */
    private long idDoToken(String token) {
        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return objectMapper.readTree(payload).get("sub").asLong();
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

    private String registrarConsumo(String token, LocalDate data, long refeicaoId) throws Exception {
        return mockMvc.perform(
                        post("/registros-diarios")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"data":"%s","refeicaoId":%d}
                                        """
                                                .formatted(data, refeicaoId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long idDe(String corpoJson) {
        return objectMapper.readTree(corpoJson).get("id").asLong();
    }

    private long idDoPrimeiroItem(String corpoJson) {
        return objectMapper.readTree(corpoJson).get("itens").get(0).get("id").asLong();
    }

    /** Forca ir no banco. Sem isso a verificacao leria o cache do Hibernate. */
    private void reiniciarContexto() {
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("Registrar refeição da biblioteca em uma data")
    class Registrar {

        @Test
        @DisplayName("Copia título, ícone e itens, devolvendo o custo do dia")
        void registraComCopiaDaComposicao() throws Exception {
            mockMvc.perform(
                            post("/registros-diarios")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"data":"%s","refeicaoId":%d}
                                            """
                                                    .formatted(HOJE, almocoId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data").value("2026-08-03"))
                    .andExpect(jsonPath("$.titulo").value("Almoço"))
                    .andExpect(jsonPath("$.icone").value("prato"))
                    .andExpect(jsonPath("$.refeicaoOrigemId").value(almocoId))
                    .andExpect(jsonPath("$.itens.length()").value(2))
                    .andExpect(jsonPath("$.custoTotal").value(3.595))
                    .andExpect(jsonPath("$.itensSemPreco.length()").value(0));
        }

        @Test
        @DisplayName("A mesma refeição pode ser registrada em dias diferentes, cada uma com itens próprios")
        void permiteRegistrarOMesmoModeloEmDiasDiferentes() throws Exception {
            long ontem = idDoPrimeiroItem(registrarConsumo(tokenAna, ONTEM, almocoId));
            long hoje = idDoPrimeiroItem(registrarConsumo(tokenAna, HOJE, almocoId));

            assertThat(ontem).isNotEqualTo(hoje);
        }

        @Test
        void refeicaoInexistenteRetorna404() throws Exception {
            mockMvc.perform(
                            post("/registros-diarios")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"data":"%s","refeicaoId":999999}
                                            """
                                                    .formatted(HOJE)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void dataAusenteRetorna400() throws Exception {
            mockMvc.perform(
                            post("/registros-diarios")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"refeicaoId":%d}
                                            """.formatted(almocoId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.data").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("O diário é cópia: editar o dia não altera a biblioteca")
    class RegraDeOuro {

        @Test
        @DisplayName("Ajustar a quantidade no diário NÃO altera o modelo da biblioteca")
        void ajusteNoDiarioNaoAlteraABiblioteca() throws Exception {
            String registro = registrarConsumo(tokenAna, HOJE, almocoId);
            long registroId = idDe(registro);
            long itemId = idDoPrimeiroItem(registro);

            mockMvc.perform(
                            patch("/registros-diarios/" + registroId + "/itens/" + itemId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[0].quantidadeConsumida").value(300))
                    // 5,67 dos 300 g de frango + 0,76 da aveia
                    .andExpect(jsonPath("$.custoTotal").value(6.43));

            reiniciarContexto();

            mockMvc.perform(
                            get("/refeicoes/" + almocoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.itens[0].quantidadeConsumida").value(150))
                    .andExpect(jsonPath("$.custoTotal").value(3.595));
        }

        @Test
        @DisplayName("Ajustar o almoço de hoje NÃO altera o almoço de ontem")
        void ajusteEmUmDiaNaoVazaParaOutroDia() throws Exception {
            String ontem = registrarConsumo(tokenAna, ONTEM, almocoId);
            String hoje = registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            patch("/registros-diarios/" + idDe(hoje) + "/itens/"
                                            + idDoPrimeiroItem(hoje))
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isOk());

            reiniciarContexto();

            mockMvc.perform(
                            get("/registros-diarios/" + idDe(ontem))
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.itens[0].quantidadeConsumida").value(150))
                    .andExpect(jsonPath("$.custoTotal").value(3.595));
        }

        @Test
        @DisplayName("Editar a biblioteca depois NÃO reescreve o dia já registrado")
        void edicaoDaBibliotecaNaoReescreveODiaRegistrado() throws Exception {
            long registroId = idDe(registrarConsumo(tokenAna, HOJE, almocoId));

            mockMvc.perform(
                            put("/refeicoes/" + almocoId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"titulo":"Almoço reformulado","icone":"salada","itens":[
                                              {"itemMercadoId":%d,"quantidadeConsumida":40,"unidade":"G"}]}
                                            """
                                                    .formatted(aveiaId)))
                    .andExpect(status().isOk());

            reiniciarContexto();

            mockMvc.perform(
                            get("/registros-diarios/" + registroId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.titulo").value("Almoço"))
                    .andExpect(jsonPath("$.itens.length()").value(2))
                    .andExpect(jsonPath("$.custoTotal").value(3.595));
        }

        @Test
        @DisplayName("Apagar o modelo da biblioteca não apaga nem esvazia o dia registrado")
        void exclusaoDoModeloPreservaODiaRegistrado() throws Exception {
            long registroId = idDe(registrarConsumo(tokenAna, HOJE, almocoId));

            mockMvc.perform(
                            delete("/refeicoes/" + almocoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            reiniciarContexto();

            mockMvc.perform(
                            get("/registros-diarios/" + registroId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.titulo").value("Almoço"))
                    .andExpect(jsonPath("$.refeicaoOrigemId").doesNotExist())
                    .andExpect(jsonPath("$.custoTotal").value(3.595));
        }

        @Test
        @DisplayName("As linhas do diário são registros próprios, não as da biblioteca")
        void itensDoDiarioSaoLinhasIndependentes() throws Exception {
            registrarConsumo(tokenAna, HOJE, almocoId);
            reiniciarContexto();

            Long itensDaBiblioteca =
                    entityManager
                            .createQuery(
                                    "select count(i) from ItemRefeicao i where i.refeicao.id = :id",
                                    Long.class)
                            .setParameter("id", almocoId)
                            .getSingleResult();

            // Conta so o que e do dono. O banco de desenvolvimento e o mesmo que o seed usa,
            // entao uma contagem geral pegaria linhas de outras contas.
            Long itensDoDiario =
                    entityManager
                            .createQuery(
                                    "select count(i) from ItemRegistro i"
                                            + " where i.registroDiario.usuario.id = :usuarioId",
                                    Long.class)
                            .setParameter("usuarioId", idDoToken(tokenAna))
                            .getSingleResult();

            assertThat(itensDaBiblioteca).isEqualTo(2L);
            assertThat(itensDoDiario).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Preço congelado no consumo (RF007)")
    class PrecoCongelado {

        /** O frango sobe de R$ 18,90 para R$ 22,50 o quilo. */
        private void reajustarFrango() throws Exception {
            mockMvc.perform(
                            put("/itens-mercado/" + frangoId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"nome":"Frango","preco":22.50,"quantidadeEmbalagem":1,"unidade":"KG"}
                                            """))
                    .andExpect(status().isOk());
            reiniciarContexto();
        }

        @Test
        @DisplayName("Reajuste no mercado NÃO reescreve o custo de um dia já registrado")
        void reajusteNaoAlteraDiaJaRegistrado() throws Exception {
            long registroId = idDe(registrarConsumo(tokenAna, HOJE, almocoId));

            reajustarFrango();

            // 150 g a R$ 18,90/kg da 2,835, mais 40 g a R$ 9,50/500 g que da 0,76. O que ja foi
            // comido custou o que custou, mudar o preco hoje nao reescreve o passado.
            mockMvc.perform(
                            get("/registros-diarios/" + registroId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.custoTotal").value(3.595))
                    .andExpect(jsonPath("$.itens[0].custo").value(2.835));
        }

        @Test
        @DisplayName("Consumo registrado depois do reajuste usa o preço novo")
        void consumoPosteriorUsaOPrecoNovo() throws Exception {
            long antes = idDe(registrarConsumo(tokenAna, ONTEM, almocoId));

            reajustarFrango();

            long depois = idDe(registrarConsumo(tokenAna, HOJE, almocoId));

            // 150 g a R$ 22,50/kg da 3,375, mais os mesmos 0,76 da aveia.
            mockMvc.perform(
                            get("/registros-diarios/" + depois)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.custoTotal").value(4.135));

            // E o dia anterior continua com o preco da epoca. Os dois valores convivem.
            mockMvc.perform(
                            get("/registros-diarios/" + antes)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.custoTotal").value(3.595));
        }

        @Test
        @DisplayName("A biblioteca continua acompanhando o preço vigente — ela é modelo do futuro")
        void bibliotecaAcompanhaOPrecoVigente() throws Exception {
            registrarConsumo(tokenAna, HOJE, almocoId);

            reajustarFrango();

            mockMvc.perform(
                            get("/refeicoes/" + almocoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.custoTotal").value(4.135));
        }

        @Test
        @DisplayName("Ajustar a quantidade do dia recalcula sobre o preço congelado, não o atual")
        void ajusteDeQuantidadeUsaOPrecoCongelado() throws Exception {
            String registro = registrarConsumo(tokenAna, HOJE, almocoId);
            long registroId = idDe(registro);
            long itemId = idDoPrimeiroItem(registro);

            reajustarFrango();

            mockMvc.perform(
                            patch("/registros-diarios/" + registroId + "/itens/" + itemId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isOk())
                    // 300 g ainda a R$ 18,90/kg da 5,67. Com o preco novo daria 6,75.
                    .andExpect(jsonPath("$.itens[0].custo").value(5.67));
        }
    }

    @Nested
    @DisplayName("Listagem por data e por intervalo")
    class Listagem {

        @Test
        void listaPorData() throws Exception {
            registrarConsumo(tokenAna, ONTEM, almocoId);
            registrarConsumo(tokenAna, HOJE, almocoId);
            registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("data", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void listaPorIntervaloOrdenadoPorData() throws Exception {
            registrarConsumo(tokenAna, ONTEM, almocoId);
            registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("inicio", ONTEM.toString())
                                    .param("fim", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].data").value("2026-08-02"))
                    .andExpect(jsonPath("$[1].data").value("2026-08-03"));
        }

        @Test
        @DisplayName("Dia sem registro devolve lista vazia, não erro")
        void diaSemRegistroDevolveListaVazia() throws Exception {
            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("data", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void semParametroRetorna400() throws Exception {
            mockMvc.perform(get("/registros-diarios").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void intervaloInvertidoRetorna400() throws Exception {
            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("inicio", HOJE.toString())
                                    .param("fim", ONTEM.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void misturarDataComIntervaloRetorna400() throws Exception {
            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("data", HOJE.toString())
                                    .param("inicio", ONTEM.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Duplicar o dia anterior")
    class DuplicarDiaAnterior {

        private void duplicarPara(LocalDate destino, int esperados) throws Exception {
            mockMvc.perform(
                            post("/registros-diarios/duplicar-dia-anterior")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"data":"%s"}
                                            """.formatted(destino)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(esperados));
        }

        @Test
        @DisplayName("Copia os registros de ontem para hoje")
        void duplicaOsRegistrosDoDiaAnterior() throws Exception {
            registrarConsumo(tokenAna, ONTEM, almocoId);
            registrarConsumo(tokenAna, ONTEM, almocoId);

            duplicarPara(HOJE, 2);

            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("data", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].custoTotal").value(3.595));
        }

        @Test
        @DisplayName("Copia o dia como ele ficou, com os ajustes pontuais — não o modelo original")
        void duplicaOsAjustesENaoOModelo() throws Exception {
            String ontem = registrarConsumo(tokenAna, ONTEM, almocoId);

            mockMvc.perform(
                            patch("/registros-diarios/" + idDe(ontem) + "/itens/"
                                            + idDoPrimeiroItem(ontem))
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isOk());

            duplicarPara(HOJE, 1);

            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("data", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$[0].itens[0].quantidadeConsumida").value(300))
                    .andExpect(jsonPath("$[0].custoTotal").value(6.43));
        }

        @Test
        @DisplayName("A cópia é independente: editar hoje não muda ontem")
        void copiaNaoCompartilhaLinhasComOOriginal() throws Exception {
            String ontem = registrarConsumo(tokenAna, ONTEM, almocoId);
            duplicarPara(HOJE, 1);

            String corpoHoje =
                    mockMvc.perform(
                                    get("/registros-diarios")
                                            .param("data", HOJE.toString())
                                            .header("Authorization", "Bearer " + tokenAna))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            var registroHoje = objectMapper.readTree(corpoHoje).get(0);
            long idHoje = registroHoje.get("id").asLong();
            long itemHoje = registroHoje.get("itens").get(0).get("id").asLong();

            assertThat(itemHoje).isNotEqualTo(idDoPrimeiroItem(ontem));

            mockMvc.perform(
                            patch("/registros-diarios/" + idHoje + "/itens/" + itemHoje)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":500,"unidade":"G"}
                                            """))
                    .andExpect(status().isOk());

            reiniciarContexto();

            mockMvc.perform(
                            get("/registros-diarios/" + idDe(ontem))
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.itens[0].quantidadeConsumida").value(150));
        }

        @Test
        @DisplayName("Sem registros ontem, devolve 400 com mensagem em vez de criar nada")
        void diaAnteriorVazioRetorna400() throws Exception {
            mockMvc.perform(
                            post("/registros-diarios/duplicar-dia-anterior")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"data":"%s"}
                                            """.formatted(HOJE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(
                            org.hamcrest.Matchers.containsString("2026-08-02")));
        }
    }

    @Nested
    @DisplayName("Ajuste de item e remoção")
    class AjusteERemocao {

        @Test
        @DisplayName("Consumir em mL um item vendido em kg é 400 também no diário")
        void grandezaIncompativelNoAjusteRetorna400() throws Exception {
            String registro = registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            patch("/registros-diarios/" + idDe(registro) + "/itens/"
                                            + idDoPrimeiroItem(registro))
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":200,"unidade":"ML"}
                                            """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void quantidadeZeroNoAjusteRetorna400() throws Exception {
            String registro = registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            patch("/registros-diarios/" + idDe(registro) + "/itens/"
                                            + idDoPrimeiroItem(registro))
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":0,"unidade":"G"}
                                            """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Item que não pertence ao registro informado é 404")
        void itemDeOutroRegistroRetorna404() throws Exception {
            String ontem = registrarConsumo(tokenAna, ONTEM, almocoId);
            String hoje = registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            patch("/registros-diarios/" + idDe(hoje) + "/itens/"
                                            + idDoPrimeiroItem(ontem))
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Remover o registro não remove o modelo da biblioteca")
        void removerRegistroPreservaABiblioteca() throws Exception {
            long registroId = idDe(registrarConsumo(tokenAna, HOJE, almocoId));

            mockMvc.perform(
                            delete("/registros-diarios/" + registroId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            get("/registros-diarios/" + registroId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNotFound());

            mockMvc.perform(
                            get("/refeicoes/" + almocoId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens.length()").value(2));
        }
    }

    @Nested
    @DisplayName("Item sem preço vinculado")
    class ItemSemPreco {

        @Test
        @DisplayName("Sai do total e é sinalizado — nunca entra como R$ 0,00")
        void itemSemVinculoNaoEntraNoTotal() throws Exception {
            Usuario ana = usuarioRepository.findByEmailIgnoreCase("ana@exemplo.com").orElseThrow();

            // A API nao consegue criar um item sem vinculo, entao o cenario e montado na mao aqui so
            // para testar esse caminho do codigo.
            RegistroDiario registro =
                    new RegistroDiario(ana, HOJE, "Jantar improvisado", "prato", null);
            registro.adicionarItem(
                    new ItemRegistro(null, "Azeite", new BigDecimal("15"), UnidadeMedida.ML));
            registroDiarioRepository.saveAndFlush(registro);

            reiniciarContexto();

            mockMvc.perform(
                            get("/registros-diarios/" + registro.getId())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.custoTotal").value(0))
                    .andExpect(jsonPath("$.itens[0].custo").doesNotExist())
                    .andExpect(jsonPath("$.itens[0].itemAtivo").value(false))
                    .andExpect(jsonPath("$.itensSemPreco[0]").value("Azeite"));
        }
    }

    @Nested
    @DisplayName("Isolamento multi-tenant")
    class Isolamento {

        @Test
        void naoPermiteRegistrarRefeicaoDeOutroUsuario() throws Exception {
            mockMvc.perform(
                            post("/registros-diarios")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"data":"%s","refeicaoId":%d}
                                            """
                                                    .formatted(HOJE, almocoId)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void listagemNaoVazaRegistrosDeOutroUsuario() throws Exception {
            registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("data", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void ajustarItemDeOutroUsuarioRetorna404() throws Exception {
            String registro = registrarConsumo(tokenAna, HOJE, almocoId);

            mockMvc.perform(
                            patch("/registros-diarios/" + idDe(registro) + "/itens/"
                                            + idDoPrimeiroItem(registro))
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void removerRegistroDeOutroUsuarioRetorna404() throws Exception {
            long registroId = idDe(registrarConsumo(tokenAna, HOJE, almocoId));

            mockMvc.perform(
                            delete("/registros-diarios/" + registroId)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Duplicar o dia anterior só enxerga os próprios registros")
        void duplicarNaoAlcancaRegistrosDeOutroUsuario() throws Exception {
            registrarConsumo(tokenAna, ONTEM, almocoId);

            mockMvc.perform(
                            post("/registros-diarios/duplicar-dia-anterior")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"data":"%s"}
                                            """.formatted(HOJE)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void semTokenRetorna401() throws Exception {
            mockMvc.perform(get("/registros-diarios").param("data", HOJE.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("N+1 na listagem")
    class PrevencaoNMaisUm {

        private long consultasDoIntervalo() throws Exception {
            reiniciarContexto();
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();

            mockMvc.perform(
                            get("/registros-diarios")
                                    .param("inicio", ONTEM.minusDays(30).toString())
                                    .param("fim", HOJE.toString())
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk());

            return stats.getPrepareStatementCount();
        }

        @Test
        @DisplayName("A consulta por intervalo não cresce com o número de registros")
        void listagemPorIntervaloNaoDisparaConsultaPorRegistro() throws Exception {
            registrarConsumo(tokenAna, HOJE, almocoId);
            long comUm = consultasDoIntervalo();

            for (int i = 1; i <= 8; i++) {
                registrarConsumo(tokenAna, HOJE.minusDays(i), almocoId);
            }
            long comNove = consultasDoIntervalo();

            assertThat(comNove)
                    .as("1 registro custou %d consultas e 9 custaram %d", comUm, comNove)
                    .isEqualTo(comUm);
            assertThat(comNove).isEqualTo(1L);
        }
    }
}
