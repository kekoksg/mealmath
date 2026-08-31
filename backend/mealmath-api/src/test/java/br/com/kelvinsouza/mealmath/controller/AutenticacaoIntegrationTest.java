package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Testa o fluxo completo de cadastro e login, passando pelo SecurityFilterChain de verdade.
 * O @Transactional faz cada teste desfazer o que criou, sem deixar usuario no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Autenticação — cadastro (RF001) e login (RF002)")
class AutenticacaoIntegrationTest {

    private static final String SENHA = "senha-forte-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;

    private String registrar(String nome, String email, String senha) throws Exception {
        return mockMvc
                .perform(
                        post("/auth/registrar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"nome":"%s","email":"%s","senha":"%s"}
                                        """
                                                .formatted(nome, email, senha)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Nested
    @DisplayName("Cadastro")
    class Cadastro {

        @Test
        @DisplayName("Cria a conta e já devolve token — o RF001 autentica automaticamente")
        void cadastroRetornaTokenEUsuario() throws Exception {
            mockMvc.perform(
                            post("/auth/registrar")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"nome":"Kelvin","email":"kelvin@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.tipo").value("Bearer"))
                    .andExpect(jsonPath("$.expiraEmSegundos").value(28800))
                    .andExpect(jsonPath("$.usuario.email").value("kelvin@exemplo.com"))
                    .andExpect(jsonPath("$.usuario.nome").value("Kelvin"));
        }

        @Test
        @DisplayName("A senha é gravada como hash bcrypt, nunca em texto puro")
        void senhaEArmazenadaComBcrypt() throws Exception {
            registrar("Kelvin", "hash@exemplo.com", SENHA);

            Usuario usuario = usuarioRepository.findByEmailIgnoreCase("hash@exemplo.com").orElseThrow();

            assertThat(usuario.getSenhaHash()).isNotEqualTo(SENHA).startsWith("$2");
            assertThat(passwordEncoder.matches(SENHA, usuario.getSenhaHash())).isTrue();
        }

        @Test
        @DisplayName("A resposta não expõe o hash da senha")
        void respostaNaoVazaHash() throws Exception {
            String corpo = registrar("Kelvin", "vazamento@exemplo.com", SENHA);

            assertThat(corpo).doesNotContain("senhaHash").doesNotContain(SENHA);
        }

        @Test
        @DisplayName("E-mail repetido é 409, orientando login")
        void emailDuplicadoRetorna409() throws Exception {
            registrar("Kelvin", "duplicado@exemplo.com", SENHA);

            mockMvc.perform(
                            post("/auth/registrar")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"nome":"Outro","email":"duplicado@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensagem").value(
                            "Este e-mail já está cadastrado. Faça login em vez de criar uma nova conta."));
        }

        @Test
        @DisplayName("Maiúsculas não criam conta paralela: e-mail é normalizado antes de gravar")
        void emailDuplicadoIgnorandoCaixa() throws Exception {
            registrar("Kelvin", "caixa@exemplo.com", SENHA);

            mockMvc.perform(
                            post("/auth/registrar")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"nome":"Outro","email":"CAIXA@Exemplo.COM","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Senha curta e e-mail inválido voltam como erro por campo")
        void dadosInvalidosRetornam400ComCampos() throws Exception {
            mockMvc.perform(
                            post("/auth/registrar")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"nome":"","email":"nao-e-email","senha":"123"}
                                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.nome").isNotEmpty())
                    .andExpect(jsonPath("$.campos.email").isNotEmpty())
                    .andExpect(jsonPath("$.campos.senha").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void loginComCredenciaisCorretasRetornaToken() throws Exception {
            registrar("Kelvin", "login@exemplo.com", SENHA);

            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"login@exemplo.com","senha":"%s"}
                                            """
                                                    .formatted(SENHA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.usuario.email").value("login@exemplo.com"));
        }

        @Test
        @DisplayName("Senha errada e e-mail inexistente devolvem o mesmo 401 e a mesma mensagem")
        void credenciaisInvalidasNaoDistinguemOCaso() throws Exception {
            registrar("Kelvin", "existe@exemplo.com", SENHA);

            String senhaErrada =
                    mockMvc.perform(
                                    post("/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("""
                                                    {"email":"existe@exemplo.com","senha":"senha-errada-123"}
                                                    """))
                            .andExpect(status().isUnauthorized())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            String usuarioInexistente =
                    mockMvc.perform(
                                    post("/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("""
                                                    {"email":"nao-existe@exemplo.com","senha":"senha-errada-123"}
                                                    """))
                            .andExpect(status().isUnauthorized())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            assertThat(senhaErrada).isEqualTo(usuarioInexistente);
        }

        @Test
        @DisplayName("O sub do token é o id do usuário, que é a base do filtro por usuário")
        void tokenCarregaOIdDoUsuarioNoSubject() throws Exception {
            String corpo = registrar("Kelvin", "claims@exemplo.com", SENHA);
            JsonNode resposta = objectMapper.readTree(corpo);

            Jwt jwt = jwtDecoder.decode(resposta.get("token").asText());

            assertThat(jwt.getSubject()).isEqualTo(resposta.get("usuario").get("id").asText());
            assertThat(jwt.getClaimAsString("email")).isEqualTo("claims@exemplo.com");
            // getClaimAsString e nao getIssuer, porque o emissor aqui e um texto simples e o
            // getIssuer so sabe devolver URL.
            assertThat(jwt.getClaimAsString("iss")).isEqualTo("mealmath-api");
            assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        }

        @Test
        @DisplayName("criadoEm vai no corpo e no token — o perfil mostra 'Membro desde' sem novo GET")
        void tokenECorpoCarregamAAberturaDaConta() throws Exception {
            String corpo = registrar("Kelvin", "membro@exemplo.com", SENHA);
            JsonNode resposta = objectMapper.readTree(corpo);

            Instant gravado =
                    usuarioRepository
                            .findByEmailIgnoreCase("membro@exemplo.com")
                            .orElseThrow()
                            .getCriadoEm();

            assertThat(Instant.parse(resposta.get("usuario").get("criadoEm").asText()))
                    .isEqualTo(gravado);

            // Vem como texto ISO-8601 e nao como numero. E assim que o front remonta a sessao.
            Jwt jwt = jwtDecoder.decode(resposta.get("token").asText());
            assertThat(Instant.parse(jwt.getClaimAsString("criadoEm"))).isEqualTo(gravado);
        }
    }

    @Nested
    @DisplayName("SecurityFilterChain")
    class FiltroDeSeguranca {

        @Test
        @DisplayName("/auth/** é público")
        void rotasDeAutenticacaoSaoLiberadas() throws Exception {
            mockMvc.perform(
                            post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"email":"qualquer@exemplo.com","senha":"senha-qualquer"}
                                            """))
                    .andExpect(status().isUnauthorized()); // 401 da credencial, não do filtro
        }

        @Test
        @DisplayName("Rota fora de /auth/** sem token é barrada com 401")
        void rotaProtegidaSemTokenRetorna401() throws Exception {
            mockMvc.perform(get("/refeicoes")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token inválido é rejeitado com 401")
        void tokenInvalidoRetorna401() throws Exception {
            mockMvc.perform(get("/refeicoes").header("Authorization", "Bearer token-falsificado"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Com token válido a requisição chega ao endpoint protegido e é atendida")
        void tokenValidoAtravessaOFiltro() throws Exception {
            String corpo = registrar("Kelvin", "filtro@exemplo.com", SENHA);
            String token = objectMapper.readTree(corpo).get("token").asText();

            mockMvc.perform(get("/refeicoes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    // Biblioteca vazia, porque o usuario acabou de ser criado e so ve o que e dele.
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
