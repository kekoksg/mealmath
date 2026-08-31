package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
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

/** Testa a edicao dos dados da propria conta, subindo a aplicacao de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Perfil — dados da conta")
class PerfilIntegrationTest {

    private static final String SENHA = "senha-forte-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;

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

    private static String corpoPerfil(String nome, String email) {
        return """
                {"nome":"%s","email":"%s"}
                """
                .formatted(nome, email);
    }

    private static String corpoSenha(String atual, String nova) {
        return """
                {"senhaAtual":"%s","novaSenha":"%s"}
                """
                .formatted(atual, nova);
    }

    @Nested
    @DisplayName("Consulta")
    class Consulta {

        @Test
        @DisplayName("GET devolve nome, e-mail e data de abertura da conta, sem o hash da senha")
        void devolveDadosDaConta() throws Exception {
            mockMvc.perform(get("/perfil").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Ana"))
                    .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
                    .andExpect(jsonPath("$.criadoEm").exists())
                    .andExpect(jsonPath("$.senhaHash").doesNotExist())
                    .andExpect(jsonPath("$.senha").doesNotExist());
        }

        @Test
        @DisplayName("Cada token enxerga apenas a própria conta")
        void perfilEhDoDonoDoToken() throws Exception {
            mockMvc.perform(get("/perfil").header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("bruno@exemplo.com"));
        }
    }

    @Nested
    @DisplayName("Edição de nome e e-mail")
    class Edicao {

        @Test
        @DisplayName("Atualiza os dados e devolve token novo já com as claims corrigidas")
        void atualizaEReemiteToken() throws Exception {
            String corpo =
                    mockMvc.perform(
                                    put("/perfil")
                                            .header("Authorization", "Bearer " + tokenAna)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(corpoPerfil("Ana Maria", "ana.maria@exemplo.com")))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.usuario.nome").value("Ana Maria"))
                            .andExpect(jsonPath("$.usuario.email").value("ana.maria@exemplo.com"))
                            .andExpect(jsonPath("$.token").isNotEmpty())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            // O token velho continua valendo ate expirar, mas com o nome antigo dentro. Por isso
            // a resposta traz um token novo, e e ele que a tela tem que passar a usar.
            String tokenNovo = objectMapper.readTree(corpo).get("token").asText();
            mockMvc.perform(get("/perfil").header("Authorization", "Bearer " + tokenNovo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Ana Maria"));
        }

        @Test
        @DisplayName("Salvar sem trocar de e-mail não acusa duplicidade do próprio e-mail")
        void manterProprioEmailNaoEhConflito() throws Exception {
            mockMvc.perform(
                            put("/perfil")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("Ana Souza", "ana@exemplo.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuario.nome").value("Ana Souza"));
        }

        @Test
        @DisplayName("E-mail já usado por outra conta é 409 e nada é gravado")
        void emailDeOutraContaEhConflito() throws Exception {
            mockMvc.perform(
                            put("/perfil")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("Ana", "bruno@exemplo.com")))
                    .andExpect(status().isConflict());

            assertThat(usuarioRepository.findByEmailIgnoreCase("bruno@exemplo.com"))
                    .get()
                    .extracting(u -> u.getNome())
                    .isEqualTo("Bruno");
        }

        @Test
        @DisplayName("E-mail é gravado em minúsculas, como no cadastro")
        void normalizaEmail() throws Exception {
            mockMvc.perform(
                            put("/perfil")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("Ana", "Ana.NOVA@Exemplo.COM")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuario.email").value("ana.nova@exemplo.com"));
        }

        @Test
        @DisplayName("E-mail com espaço nas pontas é 400 na validação, como no cadastro")
        void emailComEspacoEhInvalido() throws Exception {
            mockMvc.perform(
                            put("/perfil")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("Ana", "  ana.nova@exemplo.com  ")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.email").exists());
        }

        @Test
        @DisplayName("Trocar o e-mail passa a valer para o login")
        void loginPassaAUsarOEmailNovo() throws Exception {
            mockMvc.perform(
                            put("/perfil")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("Ana", "ana.nova@exemplo.com")))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"ana.nova@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"ana@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Nome em branco e e-mail inválido são 400 com erro por campo")
        void validaEntrada() throws Exception {
            mockMvc.perform(
                            put("/perfil")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("   ", "nao-e-email")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.nome").exists())
                    .andExpect(jsonPath("$.campos.email").exists());
        }
    }

    @Nested
    @DisplayName("Troca de senha")
    class TrocaDeSenha {

        @Test
        @DisplayName("Com a senha atual correta, a nova passa a valer no login e a antiga não")
        void trocaSenha() throws Exception {
            mockMvc.perform(
                            put("/perfil/senha")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoSenha(SENHA, "outra-senha-forte-456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"ana@exemplo.com","senha":"outra-senha-forte-456"}
                                            """))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"ana@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Senha atual errada é 400 (e não 401, que derrubaria a sessão) e não troca nada")
        void senhaAtualErradaNaoTroca() throws Exception {
            mockMvc.perform(
                            put("/perfil/senha")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoSenha("senha-errada-999", "outra-senha-forte-456")))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"ana@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Nova senha curta demais é 400 com erro no campo")
        void novaSenhaCurtaEhInvalida() throws Exception {
            mockMvc.perform(
                            put("/perfil/senha")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoSenha(SENHA, "1234")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.novaSenha").exists());
        }

        @Test
        @DisplayName("Trocar a senha de uma conta não afeta a senha da outra")
        void trocaNaoVazaEntreContas() throws Exception {
            mockMvc.perform(
                            put("/perfil/senha")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoSenha(SENHA, "outra-senha-forte-456")))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"bruno@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Autenticação obrigatória")
    class Autenticacao {

        @Test
        @DisplayName("Sem token, todas as rotas de perfil são 401")
        void exigeAutenticacao() throws Exception {
            mockMvc.perform(get("/perfil")).andExpect(status().isUnauthorized());
            mockMvc.perform(
                            put("/perfil")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoPerfil("Ana", "ana@exemplo.com")))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            put("/perfil/senha")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoSenha(SENHA, "outra-senha-forte-456")))
                    .andExpect(status().isUnauthorized());
        }
    }
}
