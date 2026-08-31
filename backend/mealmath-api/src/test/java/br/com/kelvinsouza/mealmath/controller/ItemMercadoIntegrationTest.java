package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.repository.ItemMercadoRepository;
import br.com.kelvinsouza.mealmath.repository.RefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import java.math.BigDecimal;
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

/** Testa o CRUD de item de mercado (RF004) e o historico de preco (RF007) na aplicacao real. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Itens de mercado — CRUD (RF004) e histórico de preço (RF007)")
class ItemMercadoIntegrationTest {

    private static final String SENHA = "senha-forte-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ItemMercadoRepository itemMercadoRepository;
    @Autowired private RefeicaoRepository refeicaoRepository;

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

    private static String corpoItem(String nome, String preco, String quantidade, String unidade) {
        return """
                {"nome":"%s","preco":%s,"quantidadeEmbalagem":%s,"unidade":"%s"}
                """
                .formatted(nome, preco, quantidade, unidade);
    }

    private String criarItem(String token, String nome, String preco, String qtd, String unidade)
            throws Exception {
        return mockMvc.perform(
                        post("/itens-mercado")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoItem(nome, preco, qtd, unidade)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long idDe(String corpoJson) {
        return objectMapper.readTree(corpoJson).get("id").asLong();
    }

    @Nested
    @DisplayName("Cadastro (RF004)")
    class Cadastro {

        @Test
        @DisplayName("Cria o item e devolve o custo unitário já calculado: R$ 18,90/kg → R$ 0,0189/g")
        void criaItemComCustoUnitarioCalculado() throws Exception {
            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "18.90", "1", "KG")))
                    .andExpect(status().isCreated())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nome").value("Frango"))
                    .andExpect(jsonPath("$.custoUnitario").value(0.0189))
                    .andExpect(jsonPath("$.unidadeBase").value("g"))
                    .andExpect(jsonPath("$.ativo").value(true));
        }

        @Test
        @DisplayName("Embalagem em mL: R$ 5,20 / 1000 mL → R$ 0,0052/mL")
        void custoUnitarioDeVolume() throws Exception {
            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Leite", "5.20", "1000", "ML")))
                    .andExpect(jsonPath("$.custoUnitario").value(0.0052))
                    .andExpect(jsonPath("$.unidadeBase").value("mL"));
        }

        @Test
        @DisplayName("Nome repetido é 409, sugerindo atualizar o preço (RF004)")
        void nomeDuplicadoRetorna409() throws Exception {
            criarItem(tokenAna, "Frango", "18.90", "1", "KG");

            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("frango", "20.00", "1", "KG")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensagem").value(
                            org.hamcrest.Matchers.containsString("Atualize o preço")));
        }

        @Test
        @DisplayName("Outro usuário pode usar o mesmo nome — a unicidade é por conta")
        void nomeRepetidoEntreUsuariosDiferentesEPermitido() throws Exception {
            criarItem(tokenAna, "Frango", "18.90", "1", "KG");

            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "22.00", "1", "KG")))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Preço zero é barrado na validação, não na divisão")
        void precoZeroRetorna400() throws Exception {
            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Quebrado", "0", "1", "KG")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.preco").isNotEmpty());
        }

        @Test
        void quantidadeEmbalagemZeroRetorna400() throws Exception {
            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Quebrado", "10.00", "0", "KG")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.quantidadeEmbalagem").isNotEmpty());
        }

        @Test
        @DisplayName("Unidade inexistente devolve 400 explicando as opções")
        void unidadeInvalidaRetorna400() throws Exception {
            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "18.90", "1", "QUILO")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(
                            org.hamcrest.Matchers.containsString("KG, G, L, ML ou UN")));
        }
    }

    @Nested
    @DisplayName("Isolamento multi-tenant")
    class Isolamento {

        @Test
        @DisplayName("A listagem devolve só os itens do próprio usuário")
        void listagemNaoVazaItensDeOutroUsuario() throws Exception {
            criarItem(tokenAna, "Frango", "18.90", "1", "KG");
            criarItem(tokenAna, "Aveia", "9.50", "500", "G");
            criarItem(tokenBruno, "Item do Bruno", "5.00", "1", "UN");

            mockMvc.perform(get("/itens-mercado").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].nome").value("Aveia"))
                    .andExpect(jsonPath("$[1].nome").value("Frango"));

            mockMvc.perform(get("/itens-mercado").header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Item do Bruno"));
        }

        @Test
        @DisplayName("Buscar item alheio devolve 404, não 403 — não confirma que o id existe")
        void buscarItemDeOutroUsuarioRetorna404() throws Exception {
            long idDaAna = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            get("/itens-mercado/" + idDaAna)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());
        }

        @Test
        void atualizarItemDeOutroUsuarioRetorna404() throws Exception {
            long idDaAna = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + idDaAna)
                                    .header("Authorization", "Bearer " + tokenBruno)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Sequestrado", "1.00", "1", "KG")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void desativarItemDeOutroUsuarioRetorna404() throws Exception {
            long idDaAna = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            delete("/itens-mercado/" + idDaAna)
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());

            // Para a dona o item continua aparecendo.
            mockMvc.perform(
                            get("/itens-mercado/" + idDaAna)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(true));
        }

        @Test
        void historicoDeItemDeOutroUsuarioRetorna404() throws Exception {
            long idDaAna = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            get("/itens-mercado/" + idDaAna + "/historico")
                                    .header("Authorization", "Bearer " + tokenBruno))
                    .andExpect(status().isNotFound());
        }

        @Test
        void semTokenTudoRetorna401() throws Exception {
            mockMvc.perform(get("/itens-mercado")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/itens-mercado").contentType(MediaType.APPLICATION_JSON)
                            .content(corpoItem("X", "1.00", "1", "KG")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Atualização de preço e histórico (RF007)")
    class AtualizacaoDePreco {

        @Test
        @DisplayName("O preço anterior é empilhado antes de sobrescrever")
        void alterarPrecoEmpilhaValorAnterior() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "22.50", "1", "KG")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preco").value(22.50))
                    .andExpect(jsonPath("$.custoUnitario").value(0.0225));

            mockMvc.perform(
                            get("/itens-mercado/" + id + "/historico")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].preco").value(18.90))
                    .andExpect(jsonPath("$[0].custoUnitario").value(0.0189))
                    .andExpect(jsonPath("$[0].unidade").value("KG"));
        }

        @Test
        @DisplayName("Duas altas seguidas empilham duas entradas, da mais recente para a mais antiga")
        void historicoAcumulaEmOrdemDecrescente() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            for (String preco : new String[] {"20.00", "22.50"}) {
                mockMvc.perform(
                                put("/itens-mercado/" + id)
                                        .header("Authorization", "Bearer " + tokenAna)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpoItem("Frango", preco, "1", "KG")))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(
                            get("/itens-mercado/" + id + "/historico")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].preco").value(20.00))
                    .andExpect(jsonPath("$[1].preco").value(18.90));
        }

        @Test
        @DisplayName("Renomear não gera histórico — senão o alerta de alta dispara por nada")
        void alterarSomenteONomeNaoGeraHistorico() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Peito de frango", "18.90", "1", "KG")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Peito de frango"));

            mockMvc.perform(
                            get("/itens-mercado/" + id + "/historico")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("18.90 e 18.9 são o mesmo preço: escala diferente não conta como alteração")
        void mudancaApenasDeEscalaNaoGeraHistorico() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "18.9", "1.000", "KG")))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            get("/itens-mercado/" + id + "/historico")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Mudar só a embalagem também empilha: o custo por grama mudou")
        void alterarEmbalagemComPrecoIgualGeraHistorico() throws Exception {
            long id = idDe(criarItem(tokenAna, "Leite", "5.20", "1", "L"));

            mockMvc.perform(
                            put("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Leite", "5.20", "2", "L")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.custoUnitario").value(0.0026));

            mockMvc.perform(
                            get("/itens-mercado/" + id + "/historico")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].custoUnitario").value(0.0052));
        }

        @Test
        @DisplayName("Trocar KG por G dentro da mesma grandeza é permitido e recalcula")
        void trocaDeUnidadeNaMesmaGrandezaEPermitida() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "18.90", "1000", "G")))
                    .andExpect(status().isOk())
                    // 18,90 por 1 kg e 18,90 por 1000 g dao o mesmo custo por grama.
                    .andExpect(jsonPath("$.custoUnitario").value(0.0189));
        }

        @Test
        @DisplayName("Nome que colide com outro item do mesmo usuário é 409")
        void renomearParaNomeExistenteRetorna409() throws Exception {
            criarItem(tokenAna, "Frango", "18.90", "1", "KG");
            long idAveia = idDe(criarItem(tokenAna, "Aveia", "9.50", "500", "G"));

            mockMvc.perform(
                            put("/itens-mercado/" + idAveia)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "9.50", "500", "G")))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Manter o próprio nome na atualização não dispara o 409 de duplicidade")
        void manterOProprioNomeNaoConflita() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "22.50", "1", "KG")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Exclusão lógica")
    class Desativacao {

        @Test
        @DisplayName("DELETE devolve 204 e o item some da listagem, mas continua acessível por id")
        void desativarRemoveDaListagemMasPreservaORegistro() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            delete("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/itens-mercado").header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.length()").value(0));

            // Continua no banco porque o diario ainda aponta para esse item para calcular o passado.
            mockMvc.perform(
                            get("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(false))
                    .andExpect(jsonPath("$.custoUnitario").value(0.0189));
        }

        @Test
        @DisplayName("O nome de um item desativado volta a ficar livre")
        void nomeDeItemDesativadoPodeSerReutilizado() throws Exception {
            long id = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            delete("/itens-mercado/" + id)
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "22.50", "1", "KG")))
                    .andExpect(status().isCreated());
        }

        @Test
        void itemInexistenteRetorna404() throws Exception {
            mockMvc.perform(
                            delete("/itens-mercado/999999")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Troca de grandeza em item já usado")
    class TrocaDeGrandeza {

        /**
         * O vinculo e montado direto pelos repositories. Sem esse teste o bloqueio de troca de grandeza
         * ficaria sendo codigo que nunca e executado.
         */
        private long vincularItemAUmaRefeicao(String nomeItem) throws Exception {
            long itemId = idDe(criarItem(tokenAna, nomeItem, "18.90", "1", "KG"));

            Usuario ana = usuarioRepository.findByEmailIgnoreCase("ana@exemplo.com").orElseThrow();
            ItemMercado item =
                    itemMercadoRepository.findByIdAndUsuarioId(itemId, ana.getId()).orElseThrow();

            Refeicao almoco = new Refeicao(ana, "Almoço", "prato");
            almoco.adicionarItem(new ItemRefeicao(item, new BigDecimal("150"), UnidadeMedida.G));
            refeicaoRepository.saveAndFlush(almoco);

            return itemId;
        }

        @Test
        @DisplayName("Mudar de KG para L é bloqueado com 409: invalidaria os 150 g já registrados")
        void trocarGrandezaDeItemEmUsoRetorna409() throws Exception {
            long itemId = vincularItemAUmaRefeicao("Frango");

            mockMvc.perform(
                            put("/itens-mercado/" + itemId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "18.90", "1", "L")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensagem").value(
                            org.hamcrest.Matchers.containsString("MASSA")));
        }

        @Test
        @DisplayName("Atualizar o preço de um item em uso continua liberado — é o RF007")
        void alterarPrecoDeItemEmUsoContinuaPermitido() throws Exception {
            long itemId = vincularItemAUmaRefeicao("Frango");

            mockMvc.perform(
                            put("/itens-mercado/" + itemId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "22.50", "1", "KG")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.custoUnitario").value(0.0225));
        }

        @Test
        @DisplayName("Trocar de grandeza é permitido enquanto o item não está em uso")
        void trocarGrandezaDeItemLivreEPermitido() throws Exception {
            long itemId = idDe(criarItem(tokenAna, "Frango", "18.90", "1", "KG"));

            mockMvc.perform(
                            put("/itens-mercado/" + itemId)
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Frango", "18.90", "1", "L")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unidadeBase").value("mL"));
        }
    }

    @Nested
    @DisplayName("Custo unitário na resposta")
    class CustoUnitario {

        @Test
        @DisplayName("Não é arredondado no backend: R$ 0,0189/g chegaria a R$ 0,02 com 2 casas")
        void custoUnitarioVemComEscalaCheia() throws Exception {
            String corpo = criarItem(tokenAna, "Frango", "18.90", "1", "KG");

            BigDecimal custoUnitario =
                    new BigDecimal(objectMapper.readTree(corpo).get("custoUnitario").asString());

            assertThat(custoUnitario).isEqualByComparingTo(new BigDecimal("0.0189"));
            assertThat(custoUnitario.setScale(2, java.math.RoundingMode.HALF_UP))
                    .isEqualByComparingTo(new BigDecimal("0.02"));
        }

        @Test
        @DisplayName("Item por contagem: R$ 12,00 / 12 un → R$ 1,00 por unidade")
        void custoUnitarioDeContagem() throws Exception {
            mockMvc.perform(
                            post("/itens-mercado")
                                    .header("Authorization", "Bearer " + tokenAna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(corpoItem("Ovos", "12.00", "12", "UN")))
                    .andExpect(jsonPath("$.custoUnitario").value(1.00))
                    .andExpect(jsonPath("$.unidadeBase").value("un"));
        }
    }
}
