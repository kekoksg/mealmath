package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.security.AuditoriaDeAcesso;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * A API responde 404 tanto para o id que nao existe quanto para o id que e de outra conta, e isso
 * nao muda. O que este teste cobre e a outra metade: por dentro os dois casos precisam ser
 * distinguiveis, senao a tentativa de ler dado alheio nao deixa rastro nenhum.
 *
 * O log e lido por um appender preso no logger da AuditoriaDeAcesso. Cada teste confere o par
 * (status HTTP, aviso emitido) junto, porque e a combinacao que importa: mudar so um dos dois ja
 * seria regressao.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Auditoria de acesso — 404 por fora, WARN por dentro")
class AuditoriaDeAcessoIntegrationTest {

    private static final String SENHA = "senha-forte-123";
    private static final String EMAIL_ANA = "ana@exemplo.com";
    private static final String EMAIL_BRUNO = "bruno@exemplo.com";
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 3);

    /** Id alto o bastante para nao colidir com nada gravado pelo cenario. */
    private static final long ID_INEXISTENTE = 999_999_999L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private Logger loggerDaAuditoria;
    private ListAppender<ILoggingEvent> capturador;

    private String tokenAna;
    private String tokenBruno;
    private long frangoDaAnaId;
    private long almocoDaAnaId;
    private long registroDaAnaId;
    private long itemDoRegistroDaAnaId;
    private long registroDoBrunoId;

    @BeforeEach
    void prepararCenarioEComecarACapturarOLog() throws Exception {
        tokenAna = registrar("Ana", EMAIL_ANA);
        tokenBruno = registrar("Bruno", EMAIL_BRUNO);

        frangoDaAnaId = criarItemMercado(tokenAna, "Frango", "18.90", "1", "KG");
        almocoDaAnaId = idDe(criarRefeicao(tokenAna, "Almoço", frangoDaAnaId, "150"));

        String registroDaAna = registrarConsumo(tokenAna, almocoDaAnaId);
        registroDaAnaId = idDe(registroDaAna);
        itemDoRegistroDaAnaId = idDoPrimeiroItem(registroDaAna);

        // O Bruno precisa de um diario proprio para o teste do item aninhado: e o unico jeito de
        // chegar na busca do item ja tendo passado pela checagem de dono do registro.
        long arrozDoBrunoId = criarItemMercado(tokenBruno, "Arroz", "6.00", "1", "KG");
        long jantarDoBrunoId = idDe(criarRefeicao(tokenBruno, "Jantar", arrozDoBrunoId, "100"));
        registroDoBrunoId = idDe(registrarConsumo(tokenBruno, jantarDoBrunoId));

        // So depois de montar o cenario, senao o setup entraria na contagem de avisos.
        loggerDaAuditoria = (Logger) LoggerFactory.getLogger(AuditoriaDeAcesso.class);
        capturador = new ListAppender<>();
        capturador.start();
        loggerDaAuditoria.addAppender(capturador);
    }

    @AfterEach
    void soltarOLogger() {
        loggerDaAuditoria.detachAppender(capturador);
        capturador.stop();
    }

    private List<ILoggingEvent> avisos() {
        return capturador.list.stream().filter(evento -> evento.getLevel() == Level.WARN).toList();
    }

    /** Falha de proposito se o request nao emitiu exatamente um aviso. */
    private String mensagemDoUnicoAviso() {
        assertThat(avisos()).hasSize(1);
        return avisos().get(0).getFormattedMessage();
    }

    /** O sub do JWT e o id do usuario, ver o TokenService. */
    private long idDoToken(String token) {
        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return objectMapper.readTree(payload).get("sub").asLong();
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

    private String criarRefeicao(String token, String titulo, long itemMercadoId, String quantidade)
            throws Exception {
        return mockMvc.perform(
                        post("/refeicoes")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoRefeicao(titulo, itemMercadoId, quantidade)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String corpoRefeicao(String titulo, long itemMercadoId, String quantidade) {
        return """
                {"titulo":"%s","icone":"prato","itens":[
                  {"itemMercadoId":%d,"quantidadeConsumida":%s,"unidade":"G"}]}
                """
                .formatted(titulo, itemMercadoId, quantidade);
    }

    private String registrarConsumo(String token, long refeicaoId) throws Exception {
        return mockMvc.perform(
                        post("/registros-diarios")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"data":"%s","refeicaoId":%d}
                                        """
                                                .formatted(HOJE, refeicaoId)))
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

    @Nested
    @DisplayName("Recurso existe, mas é de outra conta: 404 na resposta e WARN no log")
    class RecursoDeOutraConta {

        @Test
        @DisplayName("Buscar item de mercado alheio registra tipo, id do recurso e id do usuário")
        void buscarItemDeMercadoAlheio() throws Exception {
            mockMvc.perform(
                            get("/itens-mercado/" + frangoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Item de mercado\"")
                    .contains("recursoId=" + frangoDaAnaId)
                    .contains("usuarioId=" + idDoToken(tokenBruno));
        }

        @Test
        @DisplayName("Atualizar item de mercado alheio também vira sinal")
        void atualizarItemDeMercadoAlheio() throws Exception {
            mockMvc.perform(
                            put("/itens-mercado/" + frangoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"nome":"Sequestrado","preco":1.00,"quantidadeEmbalagem":1,"unidade":"KG"}
                                            """))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Item de mercado\"")
                    .contains("recursoId=" + frangoDaAnaId);
        }

        @Test
        @DisplayName("Buscar refeição alheia da biblioteca")
        void buscarRefeicaoAlheia() throws Exception {
            mockMvc.perform(
                            get("/refeicoes/" + almocoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Refeição\"")
                    .contains("recursoId=" + almocoDaAnaId)
                    .contains("usuarioId=" + idDoToken(tokenBruno));
        }

        @Test
        @DisplayName("Excluir refeição alheia: caminho que confere o dono por boolean, não por Optional")
        void excluirRefeicaoAlheia() throws Exception {
            mockMvc.perform(
                            delete("/refeicoes/" + almocoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Refeição\"")
                    .contains("recursoId=" + almocoDaAnaId);
        }

        @Test
        @DisplayName("Montar refeição com item de mercado de outra conta, tentando pegar o preço alheio")
        void referenciarItemDeMercadoAlheioAoMontarRefeicao() throws Exception {
            mockMvc.perform(
                            post("/refeicoes")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoRefeicao("Cópia", frangoDaAnaId, "150")))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Item de mercado\"")
                    .contains("recursoId=" + frangoDaAnaId)
                    .contains("usuarioId=" + idDoToken(tokenBruno));
        }

        @Test
        @DisplayName("Registrar no diário uma refeição da biblioteca de outra conta")
        void registrarRefeicaoAlheiaNoDiario() throws Exception {
            mockMvc.perform(
                            post("/registros-diarios")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"data":"%s","refeicaoId":%d}
                                            """
                                                    .formatted(HOJE, almocoDaAnaId)))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Refeição\"")
                    .contains("recursoId=" + almocoDaAnaId);
        }

        @Test
        @DisplayName("Remover registro do diário alheio")
        void removerRegistroDiarioAlheio() throws Exception {
            mockMvc.perform(
                            delete("/registros-diarios/" + registroDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Registro diário\"")
                    .contains("recursoId=" + registroDaAnaId)
                    .contains("usuarioId=" + idDoToken(tokenBruno));
        }

        @Test
        @DisplayName("Item de linha alheio dentro de um registro próprio é sinalizado como item, não como registro")
        void ajustarItemAlheioDentroDoProprioRegistro() throws Exception {
            mockMvc.perform(
                            patch(
                                            "/registros-diarios/"
                                                    + registroDoBrunoId
                                                    + "/itens/"
                                                    + itemDoRegistroDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .contains("tipoRecurso=\"Item do registro diário\"")
                    .contains("recursoId=" + itemDoRegistroDaAnaId)
                    .contains("usuarioId=" + idDoToken(tokenBruno));
        }
    }

    @Nested
    @DisplayName("Id que não existe: mesmo 404, mas sem WARN — senão o sinal afoga no ruído")
    class IdInexistente {

        @Test
        void itemDeMercadoInexistente() throws Exception {
            mockMvc.perform(
                            get("/itens-mercado/" + ID_INEXISTENTE)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(avisos()).isEmpty();
        }

        @Test
        void refeicaoInexistente() throws Exception {
            mockMvc.perform(
                            get("/refeicoes/" + ID_INEXISTENTE)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(avisos()).isEmpty();
        }

        @Test
        void excluirRefeicaoInexistente() throws Exception {
            mockMvc.perform(
                            delete("/refeicoes/" + ID_INEXISTENTE)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(avisos()).isEmpty();
        }

        @Test
        void registroDiarioInexistente() throws Exception {
            mockMvc.perform(
                            delete("/registros-diarios/" + ID_INEXISTENTE)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(avisos()).isEmpty();
        }

        @Test
        @DisplayName("Item que não existe dentro de um registro próprio é erro de navegação, não sinal")
        void itemInexistenteDentroDoProprioRegistro() throws Exception {
            mockMvc.perform(
                            patch(
                                            "/registros-diarios/"
                                                    + registroDoBrunoId
                                                    + "/itens/"
                                                    + ID_INEXISTENTE)
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"quantidadeConsumida":300,"unidade":"G"}
                                            """))
                    .andExpect(status().isNotFound());

            assertThat(avisos()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Limites do log")
    class LimitesDoLog {

        @Test
        @DisplayName("O aviso leva só ids: nada de e-mail, nome ou senha")
        void avisoNaoCarregaDadoPessoal() throws Exception {
            mockMvc.perform(
                            get("/itens-mercado/" + frangoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            assertThat(mensagemDoUnicoAviso())
                    .doesNotContain(EMAIL_ANA)
                    .doesNotContain(EMAIL_BRUNO)
                    .doesNotContain(SENHA)
                    .doesNotContain("Ana")
                    .doesNotContain("Bruno")
                    .doesNotContain("Frango")
                    .doesNotContain(tokenBruno);
        }

        @Test
        @DisplayName("Requisição que acha o recurso não emite nada — o WARN é exceção, não rotina")
        void requisicaoBemSucedidaNaoGeraAviso() throws Exception {
            mockMvc.perform(
                            get("/itens-mercado/" + frangoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            get("/refeicoes/" + almocoDaAnaId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            get("/registros-diarios/" + registroDaAnaId)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk());

            assertThat(capturador.list).isEmpty();
        }
    }
}
