package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.repository.MetaOrcamentoRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

/** Testa o CRUD da meta de orcamento (RF010), subindo a aplicacao de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Meta de orçamento — CRUD (RF010)")
class MetaOrcamentoIntegrationTest {

    private static final String SENHA = "senha-forte-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MetaOrcamentoRepository metaOrcamentoRepository;

    private String tokenAna;
    private String tokenBruno;

    @BeforeEach
    void autenticarDoisUsuarios() throws Exception {
        tokenAna = registrar("Ana", "ana@exemplo.com");
        tokenBruno = registrar("Bruno", "bruno@exemplo.com");
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

    private static String corpoMeta(String valor, String periodo) {
        return """
                {"valor":%s,"periodo":"%s"}
                """
                .formatted(valor, periodo);
    }

    /** O sub do JWT e o id do usuario, ver o TokenService. */
    private long idDoToken(String token) throws Exception {
        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return objectMapper.readTree(payload).get("sub").asLong();
    }

    /**
     * Busca as metas do dono do token.
     *
     * Filtro pelo usuario de proposito. Os testes rodam no mesmo banco de desenvolvimento onde o
     * seed grava dados, entao um findAll contaria metas de outras contas e o teste quebraria por
     * dado que sobrou, e nao por um erro de verdade.
     */
    private long metasDe(String token) throws Exception {
        long usuarioId = idDoToken(token);
        return metaOrcamentoRepository.findAll().stream()
                .filter(meta -> meta.getUsuario().getId().equals(usuarioId))
                .count();
    }

    private void definirMeta(String token, String valor, String periodo) throws Exception {
        mockMvc.perform(
                        put("/meta-orcamento")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoMeta(valor, periodo)))
                .andExpect(status().is2xxSuccessful());
    }

    @Nested
    @DisplayName("Definição (RF010)")
    class Definicao {

        @Test
        @DisplayName("Primeira definição devolve 201 com o valor e o período salvos")
        void criaMeta() throws Exception {
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("450.00", "MENSAL")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.valor").value(450.00))
                    .andExpect(jsonPath("$.periodo").value("MENSAL"))
                    .andExpect(jsonPath("$.id").isNumber());
        }

        @Test
        @DisplayName("Redefinir atualiza a meta existente e devolve 200, não cria uma segunda")
        void atualizaEmVezDeCriar() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");

            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("500.00", "MENSAL")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value(500.00));

            assertThat(metasDe(tokenAna)).isEqualTo(1);
        }

        @Test
        @DisplayName("Trocar MENSAL por SEMANAL reescreve a mesma meta — os dois não coexistem")
        void trocarPeriodoNaoCriaSegundaMeta() throws Exception {
            String primeira =
                    mockMvc.perform(
                                    put("/meta-orcamento")
                                            .header("Authorization", "Bearer " + tokenAna)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(corpoMeta("450.00", "MENSAL")))
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            long idOriginal = objectMapper.readTree(primeira).get("id").asLong();

            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("120.00", "SEMANAL")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(idOriginal))
                    .andExpect(jsonPath("$.periodo").value("SEMANAL"))
                    .andExpect(jsonPath("$.valor").value(120.00));

            assertThat(metasDe(tokenAna)).isEqualTo(1);
        }

        @Test
        @DisplayName("Valor zero é barrado na validação, com erro no campo (RF010)")
        void valorZeroEhInvalido() throws Exception {
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("0", "MENSAL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.valor").exists());

            assertThat(metasDe(tokenAna)).isZero();
        }

        @Test
        @DisplayName("Valor negativo é barrado na validação (RF010)")
        void valorNegativoEhInvalido() throws Exception {
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("-10.00", "MENSAL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.valor").exists());
        }

        @Test
        @DisplayName("Mais de 2 casas decimais é 400, em vez de virar 251,00 sem avisar")
        void valorComCasasDemaisEhInvalido() throws Exception {
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("250.999", "MENSAL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.valor").exists());
        }

        @Test
        @DisplayName("Período ausente é 400 pedindo SEMANAL ou MENSAL")
        void periodoObrigatorio() throws Exception {
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"valor":450.00}
                                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.periodo").exists());
        }

        @Test
        @DisplayName("Período inexistente (ex.: DIARIO) devolve 400, não 500")
        void periodoInvalidoEhBadRequest() throws Exception {
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("450.00", "DIARIO")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Consulta e remoção")
    class ConsultaERemocao {

        @Test
        @DisplayName("Sem meta definida devolve 204 sem corpo — o dashboard oculta o progresso")
        void semMetaDevolve204() throws Exception {
            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Com meta definida devolve 200 com valor e período")
        void devolveMetaVigente() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");

            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value(450.00))
                    .andExpect(jsonPath("$.periodo").value("MENSAL"));
        }

        @Test
        @DisplayName("DELETE devolve 204 e a consulta volta ao estado 'não definida'")
        void removeMeta() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");

            mockMvc.perform(delete("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());
            assertThat(metasDe(tokenAna)).isZero();
        }

        @Test
        @DisplayName("DELETE sem meta definida é 404")
        void removerSemMetaEh404() throws Exception {
            mockMvc.perform(delete("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Isolamento multi-tenant")
    class Isolamento {

        @Test
        @DisplayName("Cada usuário enxerga apenas a própria meta")
        void metaEhPorConta() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");
            definirMeta(tokenBruno, "120.00", "SEMANAL");

            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value(450.00))
                    .andExpect(jsonPath("$.periodo").value("MENSAL"));

            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value(120.00))
                    .andExpect(jsonPath("$.periodo").value("SEMANAL"));
        }

        @Test
        @DisplayName("A meta de um usuário não é vista nem apagada por outro")
        void metaDeOutroUsuarioNaoEhAfetada() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");

            // Bruno nao tem meta, entao o GET dele da 204 e o DELETE da 404, mesmo com a meta
            // da Ana gravada no banco.
            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/meta-orcamento").header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/meta-orcamento").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value(450.00));
        }

        @Test
        @DisplayName("Bruno definir a meta dele não sobrescreve a de Ana")
        void metasCoexistemEntreUsuarios() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");

            mockMvc.perform(
                            put("/meta-orcamento")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("120.00", "SEMANAL")))
                    .andExpect(status().isCreated());

            assertThat(metasDe(tokenAna)).isEqualTo(1);
            assertThat(metasDe(tokenBruno)).isEqualTo(1);
        }

        @Test
        @DisplayName("Sem token, todas as rotas da meta são 401")
        void exigeAutenticacao() throws Exception {
            mockMvc.perform(get("/meta-orcamento")).andExpect(status().isUnauthorized());
            mockMvc.perform(
                            put("/meta-orcamento")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoMeta("450.00", "MENSAL")))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/meta-orcamento")).andExpect(status().isUnauthorized());
        }
    }
}
