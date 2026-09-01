package br.com.kelvinsouza.mealmath.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Testa a consolidacao de custo por periodo (RF006) na aplicacao real.
 *
 * As datas sao relativas a hoje porque o endpoint consolida os ultimos N dias ate a data atual.
 * Com data fixa, tipo 2026-08-03, os testes passariam hoje e quebrariam amanha.
 *
 * Os numeros do cenario (conferidos na mao):
 * Frango  R$ 18,90 / 1 kg   = 0,0189/g   x 150 g  = 2,835  (PROTEINA)
 * Aveia   R$  9,50 / 500 g  = 0,0190/g   x  40 g  = 0,760  (CARBOIDRATO)
 * Leite   R$  5,20 / 1 L    = 0,0052/mL  x 200 mL = 1,040  (LATICINIO)
 *
 * Almoco = frango + aveia = 3,595 e Cafe = leite = 1,040
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Dashboard — consolidação por período (RF006)")
class DashboardIntegrationTest {
    private static final String SENHA = "senha-forte-123";
    private static final LocalDate HOJE = LocalDate.now();

    /**
     * Domingo e sabado da semana de hoje: e a janela que o dashboard consolida em SEMANA.
     *
     * Os registros da semana sao ancorados no domingo, e nao em HOJE menos um dia, porque
     * "ontem" cai na semana passada quando a suite roda num domingo — o teste passaria seis
     * dias e falharia no setimo.
     */
    private static final LocalDate DOMINGO =
            HOJE.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

    private static final LocalDate SABADO = DOMINGO.plusDays(6);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @PersistenceContext private EntityManager entityManager;

    private String tokenAna;
    private String tokenBruno;
    private long almocoId;
    private long cafeId;
    private long frangoId;
    private long leiteId;

    @BeforeEach
    void prepararCenario() throws Exception {
        tokenAna = registrar("Ana", "ana@exemplo.com");
        tokenBruno = registrar("Bruno", "bruno@exemplo.com");

        frangoId = criarItemMercado(tokenAna, "Frango", "18.90", "1", "KG", "PROTEINA");
        long aveiaId = criarItemMercado(tokenAna, "Aveia", "9.50", "500", "G", "CARBOIDRATO");
        leiteId = criarItemMercado(tokenAna, "Leite", "5.20", "1", "L", "LATICINIO");

        almocoId =
                criarRefeicao(
                        tokenAna,
                        """
                        {"titulo":"Almoço","icone":"prato","itens":[
                          {"itemMercadoId":%d,"quantidadeConsumida":150,"unidade":"G"},
                          {"itemMercadoId":%d,"quantidadeConsumida":40,"unidade":"G"}]}
                        """
                                .formatted(frangoId, aveiaId));

        cafeId =
                criarRefeicao(
                        tokenAna,
                        """
                        {"titulo":"Café","icone":"cafe","itens":[
                          {"itemMercadoId":%d,"quantidadeConsumida":200,"unidade":"ML"}]}
                        """
                                .formatted(leiteId));
    }

    /** Almoco + Cafe no domingo e Almoco na segunda = R$ 8,23 em 2 dias registrados. */
    private void registrarSemanaDaAna() throws Exception {
        registrarNoDiario(tokenAna, DOMINGO, almocoId);
        registrarNoDiario(tokenAna, DOMINGO, cafeId);
        registrarNoDiario(tokenAna, DOMINGO.plusDays(1), almocoId);
    }

    @Nested
    @DisplayName("Custo do período, média por dia e completude do diário")
    class Consolidacao {
        @Test
        @DisplayName("Soma o consumo dos 7 dias e devolve a janela consolidada")
        void somaConsumoDoPeriodo() throws Exception {
            registrarSemanaDaAna();

            JsonNode dashboard = consultar(tokenAna, "SEMANA");

            assertThat(dashboard.get("periodo").asText()).isEqualTo("SEMANA");
            // A janela e a semana do calendario inteira, entao vai ate sabado mesmo que ele
            // ainda nao tenha chegado — nao ha registro no futuro para somar.
            assertThat(dashboard.get("inicio").asText()).isEqualTo(DOMINGO.toString());
            assertThat(dashboard.get("fim").asText()).isEqualTo(SABADO.toString());
            assertThat(decimal(dashboard, "custoTotal")).isEqualByComparingTo("8.23");
        }

        @Test
        @DisplayName("A média divide pelos dias da janela, não pelos dias registrados")
        void mediaDividePelosDiasDaJanela() throws Exception {
            registrarSemanaDaAna();

            JsonNode dashboard = consultar(tokenAna, "SEMANA");

            // 8,23 / 7 dias da 1,17571... Dividindo pelos 2 dias registrados daria 4,115 e
            // esconderia justamente a falta que o indicador de completude serve para mostrar.
            assertThat(decimal(dashboard, "custoMedioPorDia"))
                    .isCloseTo(new BigDecimal("1.175714"), within(new BigDecimal("0.000001")));
        }

        @Test
        @DisplayName("Completude expõe a lacuna: 2 de 7 dias com registro")
        void completudeContaDiasDistintos() throws Exception {
            registrarSemanaDaAna();

            JsonNode completude = consultar(tokenAna, "SEMANA").get("completude");

            // Duas refeicoes no mesmo dia contam como UM dia registrado.
            assertThat(completude.get("diasComRegistro").asInt()).isEqualTo(2);
            assertThat(completude.get("totalDeDias").asInt()).isEqualTo(7);
        }

        @Test
        @DisplayName("Cada período tem sua janela: DIA = hoje, SEMANA = 7 dias, MES = 30 dias")
        void janelaPorPeriodo() throws Exception {
            JsonNode dia = consultar(tokenAna, "DIA");
            assertThat(dia.get("inicio").asText()).isEqualTo(HOJE.toString());
            assertThat(dia.get("completude").get("totalDeDias").asInt()).isEqualTo(1);

            JsonNode mes = consultar(tokenAna, "MES");
            assertThat(mes.get("inicio").asText()).isEqualTo(HOJE.minusDays(29).toString());
            assertThat(mes.get("completude").get("totalDeDias").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("Sem parâmetro, o período padrão é SEMANA")
        void periodoPadraoEhSemana() throws Exception {
            String corpo =
                    mockMvc.perform(get("/dashboard").header("Authorization", "Bearer " + tokenAna))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            assertThat(objectMapper.readTree(corpo).get("periodo").asText()).isEqualTo("SEMANA");
        }

        @Test
        @DisplayName("Período sem registros devolve 200 com zeros — vazio é estado, não erro")
        void periodoVazioNaoEhErro() throws Exception {
            JsonNode dashboard = consultar(tokenAna, "SEMANA");

            assertThat(decimal(dashboard, "custoTotal")).isEqualByComparingTo("0");
            assertThat(decimal(dashboard, "custoMedioPorDia")).isEqualByComparingTo("0");
            assertThat(dashboard.get("completude").get("diasComRegistro").asInt()).isZero();
            assertThat(dashboard.get("composicaoPorCategoria")).isEmpty();
            assertThat(dashboard.get("itensMaiorImpacto")).isEmpty();
        }

        @Test
        @DisplayName("Consumo fora da janela não entra no total")
        void consumoForaDaJanelaNaoEntra() throws Exception {
            registrarNoDiario(tokenAna, HOJE.minusDays(7), almocoId);

            // hoje-7 e o primeiro dia do periodo ANTERIOR a janela de 7 dias.
            assertThat(decimal(consultar(tokenAna, "SEMANA"), "custoTotal")).isEqualByComparingTo("0");
            assertThat(decimal(consultar(tokenAna, "MES"), "custoTotal")).isEqualByComparingTo("3.595");
        }
    }

    @Nested
    @DisplayName("Comparação com o período anterior")
    class PeriodoAnterior {
        @Test
        @DisplayName("Compara com o bloco imediatamente anterior, de mesma duração")
        void comparaComBlocoAnterior() throws Exception {
            registrarSemanaDaAna();
            registrarNoDiario(tokenAna, DOMINGO.minusDays(7), almocoId); // 3,595 na semana passada

            JsonNode comparativo = consultar(tokenAna, "SEMANA").get("comparativo");

            assertThat(comparativo.get("inicio").asText())
                    .isEqualTo(DOMINGO.minusDays(7).toString());
            assertThat(comparativo.get("fim").asText()).isEqualTo(DOMINGO.minusDays(1).toString());
            assertThat(comparativo.get("custoTotal").decimalValue()).isEqualByComparingTo("3.595");

            // (8,23 - 3,595) / 3,595 = +128,929...%, que com 2 casas vira 128,93.
            assertThat(comparativo.get("variacaoPercentual").decimalValue())
                    .isEqualByComparingTo("128.93");
        }

        @Test
        @DisplayName("Queda de gasto vira variação negativa")
        void quedaDeGastoEhNegativa() throws Exception {
            registrarNoDiario(tokenAna, HOJE, cafeId); // 1,040 agora
            registrarNoDiario(tokenAna, HOJE.minusDays(7), almocoId); // 3,595 antes

            JsonNode comparativo = consultar(tokenAna, "SEMANA").get("comparativo");

            // (1,04 - 3,595) / 3,595 = -71,0709...%, que vira -71,07.
            assertThat(comparativo.get("variacaoPercentual").decimalValue())
                    .isEqualByComparingTo("-71.07");
        }

        @Test
        @DisplayName(
                "Caso de borda: sem período anterior, o comparativo é omitido em vez de +100%"
                        + "")
        void semPeriodoAnteriorOcultaComparativo() throws Exception {
            registrarSemanaDaAna(); // nada na semana passada

            JsonNode dashboard = consultar(tokenAna, "SEMANA");

            assertThat(ausente(dashboard, "comparativo")).isTrue();
            // O custo do periodo atual continua sendo calculado normal, so a comparacao some.
            assertThat(decimal(dashboard, "custoTotal")).isEqualByComparingTo("8.23");
        }

        @Test
        @DisplayName("Caso de borda: período anterior existe mas soma zero — sem denominador, sem %")
        void periodoAnteriorZeradoOcultaComparativo() throws Exception {
            registrarSemanaDaAna();
            registrarNoDiario(tokenAna, DOMINGO.minusDays(7), almocoId);
            // O unico consumo do periodo anterior perde o vinculo de preco, entao o total dele
            // vai a zero e a divisao ficaria sem denominador.
            desvincularPrecoDoItem("Frango");
            desvincularPrecoDoItem("Aveia");

            assertThat(ausente(consultar(tokenAna, "SEMANA"), "comparativo")).isTrue();
        }
    }

    @Nested
    @DisplayName("Composição por categoria e itens de maior impacto")
    class Composicao {
        @Test
        @DisplayName("Agrupa o gasto por categoria, da maior fatia para a menor")
        void composicaoPorCategoria() throws Exception {
            registrarSemanaDaAna();

            JsonNode fatias = consultar(tokenAna, "SEMANA").get("composicaoPorCategoria");

            assertThat(fatias).hasSize(3);

            // Frango: 2,835 x 2 dias = 5,67, que da 68,89% de 8,23.
            assertThat(fatias.get(0).get("categoria").asText()).isEqualTo("PROTEINA");
            assertThat(fatias.get(0).get("rotulo").asText()).isEqualTo("Proteína");
            assertThat(fatias.get(0).get("custo").decimalValue()).isEqualByComparingTo("5.67");
            assertThat(fatias.get(0).get("percentual").decimalValue()).isEqualByComparingTo("68.89");

            // Aveia: 0,76 x 2 = 1,52, que da 18,47%.
            assertThat(fatias.get(1).get("categoria").asText()).isEqualTo("CARBOIDRATO");
            assertThat(fatias.get(1).get("custo").decimalValue()).isEqualByComparingTo("1.52");
            assertThat(fatias.get(1).get("percentual").decimalValue()).isEqualByComparingTo("18.47");

            // Leite: 1,04 x 1, que da 12,64%.
            assertThat(fatias.get(2).get("categoria").asText()).isEqualTo("LATICINIO");
            assertThat(fatias.get(2).get("custo").decimalValue()).isEqualByComparingTo("1.04");
            assertThat(fatias.get(2).get("percentual").decimalValue()).isEqualByComparingTo("12.64");
        }

        @Test
        @DisplayName("Item cadastrado sem categoria cai em OUTROS, para as fatias fecharem 100%")
        void itemSemCategoriaVaiParaOutros() throws Exception {
            long cafeEmPo = criarItemMercado(tokenAna, "Café em pó", "22.00", "500", "G", null);
            long refeicao =
                    criarRefeicao(
                            tokenAna,
                            """
                            {"titulo":"Cafezinho","itens":[
                              {"itemMercadoId":%d,"quantidadeConsumida":10,"unidade":"G"}]}
                            """
                                    .formatted(cafeEmPo));
            registrarNoDiario(tokenAna, HOJE, refeicao);

            JsonNode fatias = consultar(tokenAna, "SEMANA").get("composicaoPorCategoria");

            assertThat(fatias).hasSize(1);
            assertThat(fatias.get(0).get("categoria").asText()).isEqualTo("OUTROS");
            assertThat(fatias.get(0).get("percentual").decimalValue()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Ranking de impacto acumula o item ao longo do período, não por refeição")
        void itensMaiorImpacto() throws Exception {
            registrarSemanaDaAna();

            JsonNode itens = consultar(tokenAna, "SEMANA").get("itensMaiorImpacto");

            assertThat(itens).hasSize(3);
            assertThat(itens.get(0).get("nome").asText()).isEqualTo("Frango");
            assertThat(itens.get(0).get("itemMercadoId").asLong()).isEqualTo(frangoId);
            assertThat(itens.get(0).get("categoria").asText()).isEqualTo("PROTEINA");
            assertThat(itens.get(0).get("custo").decimalValue()).isEqualByComparingTo("5.67");
            assertThat(itens.get(1).get("nome").asText()).isEqualTo("Aveia");
            assertThat(itens.get(2).get("nome").asText()).isEqualTo("Leite");
        }

        @Test
        @DisplayName(
                "Caso de borda: item sem preço sai do total e é sinalizado, nunca vira R$ 0,00"
                        + "")
        void itemSemPrecoSaiDoTotalESinaliza() throws Exception {
            registrarNoDiario(tokenAna, HOJE, almocoId);
            desvincularPrecoDoItem("Aveia");

            JsonNode dashboard = consultar(tokenAna, "SEMANA");

            // 3,595 menos os 0,76 da aveia da 2,835. O total diminui, mas com aviso na tela.
            assertThat(decimal(dashboard, "custoTotal")).isEqualByComparingTo("2.835");
            assertThat(dashboard.get("itensSemPreco")).hasSize(1);
            assertThat(dashboard.get("itensSemPreco").get(0).asText()).isEqualTo("Aveia");
            // E o item nao entra em nenhuma fatia do grafico nem no ranking.
            assertThat(dashboard.get("composicaoPorCategoria")).hasSize(1);
            assertThat(dashboard.get("itensMaiorImpacto")).hasSize(1);
        }

        @Test
        @DisplayName("O mesmo item faltando em vários dias é um aviso só, não um por linha")
        void itensSemPrecoNaoRepetem() throws Exception {
            registrarNoDiario(tokenAna, HOJE, almocoId);
            registrarNoDiario(tokenAna, HOJE.minusDays(1), almocoId);
            desvincularPrecoDoItem("Aveia");

            assertThat(consultar(tokenAna, "SEMANA").get("itensSemPreco")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Progresso da meta de orçamento (RF009)")
    class Meta {
        @Test
        @DisplayName(
                "Caso de borda: sem meta definida, o progresso é omitido em vez de 0%"
                        + "")
        void semMetaOcultaProgresso() throws Exception {
            registrarSemanaDaAna();

            assertThat(ausente(consultar(tokenAna, "SEMANA"), "meta")).isTrue();
        }

        @Test
        @DisplayName("Meta mensal é rateada para a janela de 7 dias: R$ 450,00/mês → R$ 105,00")
        void metaMensalEhRateadaParaAJanela() throws Exception {
            registrarSemanaDaAna();
            definirMeta(tokenAna, "450.00", "MENSAL");

            JsonNode meta = consultar(tokenAna, "SEMANA").get("meta");

            assertThat(meta.get("periodo").asText()).isEqualTo("MENSAL");
            assertThat(meta.get("valor").decimalValue()).isEqualByComparingTo("450.00");
            // 450 / 30 dias x 7 dias = 105,00. Comparar 7 dias com a meta cheia do mes diria
            // "1,8% da meta" e passaria uma folga que nao existe.
            assertThat(meta.get("valorNoPeriodo").decimalValue()).isEqualByComparingTo("105.00");
            assertThat(meta.get("percentualConsumido").decimalValue()).isEqualByComparingTo("7.84");
            assertThat(meta.get("saldo").decimalValue()).isEqualByComparingTo("96.77");
            assertThat(meta.get("acimaDaMeta").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Meta semanal na janela de 7 dias vale o valor cheio")
        void metaSemanalNaJanelaSemanal() throws Exception {
            registrarSemanaDaAna();
            definirMeta(tokenAna, "20.00", "SEMANAL");

            JsonNode meta = consultar(tokenAna, "SEMANA").get("meta");

            assertThat(meta.get("valorNoPeriodo").decimalValue()).isEqualByComparingTo("20.00");
            // 8,23 / 20,00 = 41,15%.
            assertThat(meta.get("percentualConsumido").decimalValue()).isEqualByComparingTo("41.15");
            assertThat(meta.get("saldo").decimalValue()).isEqualByComparingTo("11.77");
        }

        @Test
        @DisplayName("Estouro passa de 100% e devolve saldo negativo, sem travar em 100%")
        void estouroDeMetaNaoEhLimitadoA100() throws Exception {
            registrarSemanaDaAna();
            definirMeta(tokenAna, "5.00", "SEMANAL");

            JsonNode meta = consultar(tokenAna, "SEMANA").get("meta");

            // 8,23 / 5,00 = 164,60%. Se travasse em 100% nao daria para ver o tamanho do estouro.
            assertThat(meta.get("percentualConsumido").decimalValue()).isEqualByComparingTo("164.60");
            assertThat(meta.get("saldo").decimalValue()).isEqualByComparingTo("-3.23");
            assertThat(meta.get("acimaDaMeta").asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("Alertas de alta de preço (RF007)")
    class AltasDePreco {
        @Test
        @DisplayName("Compara os dois últimos valores da série: histórico mais recente × preço vigente")
        void comparaUltimosDoisValores() throws Exception {
            atualizarItemMercado(tokenAna, frangoId, "Frango", "22.00", "1", "KG", "PROTEINA");

            JsonNode altas = consultar(tokenAna, "SEMANA").get("altasDePreco");

            assertThat(altas).hasSize(1);
            assertThat(altas.get(0).get("nome").asText()).isEqualTo("Frango");
            assertThat(altas.get(0).get("precoAnterior").decimalValue()).isEqualByComparingTo("18.90");
            assertThat(altas.get(0).get("precoAtual").decimalValue()).isEqualByComparingTo("22.00");
            assertThat(altas.get(0).get("unidadeBase").asText()).isEqualTo("g");
            // De 0,0189/g para 0,0220/g da +16,40%.
            assertThat(altas.get(0).get("variacaoPercentual").decimalValue())
                    .isEqualByComparingTo("16.40");
        }

        @Test
        @DisplayName("Usa só a troca mais recente, ignorando os preços mais antigos do histórico")
        void usaApenasATrocaMaisRecente() throws Exception {
            atualizarItemMercado(tokenAna, frangoId, "Frango", "30.00", "1", "KG", "PROTEINA");
            atualizarItemMercado(tokenAna, frangoId, "Frango", "33.00", "1", "KG", "PROTEINA");

            JsonNode alta = consultar(tokenAna, "SEMANA").get("altasDePreco").get(0);

            // A comparacao e de 30,00 para 33,00 (+10%), e nao de 18,90 para 33,00 (+74,6%).
            assertThat(alta.get("precoAnterior").decimalValue()).isEqualByComparingTo("30.00");
            assertThat(alta.get("variacaoPercentual").decimalValue()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Embalagem maior pelo mesmo dinheiro é queda de custo, não alta de preço")
        void embalagemMaiorNaoEhAlta() throws Exception {
            // O preco da etiqueta sobe 7,7% (de 5,20 para 5,60), mas o custo por mL cai de
            // 0,0052 para 0,0028.
            atualizarItemMercado(tokenAna, leiteId, "Leite", "5.60", "2", "L", "LATICINIO");

            assertThat(consultar(tokenAna, "SEMANA").get("altasDePreco")).isEmpty();
        }

        @Test
        @DisplayName("Queda de preço não vira alerta")
        void quedaDePrecoNaoEhAlerta() throws Exception {
            atualizarItemMercado(tokenAna, frangoId, "Frango", "15.00", "1", "KG", "PROTEINA");

            assertThat(consultar(tokenAna, "SEMANA").get("altasDePreco")).isEmpty();
        }

        @Test
        @DisplayName("Item nunca reprecificado não aparece: sem dois valores não há variação")
        void itemSemHistoricoNaoAparece() throws Exception {
            registrarSemanaDaAna();

            assertThat(consultar(tokenAna, "SEMANA").get("altasDePreco")).isEmpty();
        }

        @Test
        @DisplayName("Ordena da maior alta para a menor")
        void ordenaPelaMaiorAlta() throws Exception {
            atualizarItemMercado(tokenAna, frangoId, "Frango", "20.79", "1", "KG", "PROTEINA"); // +10%
            atualizarItemMercado(tokenAna, leiteId, "Leite", "7.80", "1", "L", "LATICINIO"); // +50%

            JsonNode altas = consultar(tokenAna, "SEMANA").get("altasDePreco");

            assertThat(altas).hasSize(2);
            assertThat(altas.get(0).get("nome").asText()).isEqualTo("Leite");
            assertThat(altas.get(0).get("variacaoPercentual").decimalValue())
                    .isEqualByComparingTo("50.00");
            assertThat(altas.get(1).get("nome").asText()).isEqualTo("Frango");
            assertThat(altas.get(1).get("variacaoPercentual").decimalValue())
                    .isEqualByComparingTo("10.00");
        }
    }

    @Nested
    @DisplayName("Isolamento multi-tenant")
    class Isolamento {
        @Test
        @DisplayName("O consumo de um usuário não entra no dashboard do outro")
        void consumoNaoVazaEntreUsuarios() throws Exception {
            registrarSemanaDaAna();

            long carneId = criarItemMercado(tokenBruno, "Carne", "40.00", "1", "KG", "PROTEINA");
            long jantarId =
                    criarRefeicao(
                            tokenBruno,
                            """
                            {"titulo":"Jantar","itens":[
                              {"itemMercadoId":%d,"quantidadeConsumida":200,"unidade":"G"}]}
                            """
                                    .formatted(carneId));
            registrarNoDiario(tokenBruno, HOJE, jantarId);

            assertThat(decimal(consultar(tokenAna, "SEMANA"), "custoTotal"))
                    .isEqualByComparingTo("8.23");
            assertThat(decimal(consultar(tokenBruno, "SEMANA"), "custoTotal"))
                    .isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("A alta de preço de um usuário não aparece no alerta do outro")
        void alertaDePrecoNaoVazaEntreUsuarios() throws Exception {
            atualizarItemMercado(tokenAna, frangoId, "Frango", "22.00", "1", "KG", "PROTEINA");

            assertThat(consultar(tokenAna, "SEMANA").get("altasDePreco")).hasSize(1);
            assertThat(consultar(tokenBruno, "SEMANA").get("altasDePreco")).isEmpty();
        }

        @Test
        @DisplayName("A meta de um usuário não é aplicada ao dashboard do outro")
        void metaNaoVazaEntreUsuarios() throws Exception {
            definirMeta(tokenAna, "450.00", "MENSAL");

            assertThat(ausente(consultar(tokenAna, "SEMANA"), "meta")).isFalse();
            assertThat(ausente(consultar(tokenBruno, "SEMANA"), "meta")).isTrue();
        }

        @Test
        @DisplayName("Sem token, o dashboard é 401")
        void exigeAutenticacao() throws Exception {
            mockMvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Validação de entrada")
    class Validacao {
        @Test
        @DisplayName("Período inexistente devolve 400 listando os aceitos, não 500")
        void periodoInvalidoEhBadRequest() throws Exception {
            mockMvc.perform(
                            get("/dashboard")
                                    .param("periodo", "ANO")
                                    .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isBadRequest());
        }
    }

    private JsonNode consultar(String token, String periodo) throws Exception {
        String corpo =
                mockMvc.perform(
                                get("/dashboard")
                                        .param("periodo", periodo)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(corpo);
    }

    private static BigDecimal decimal(JsonNode no, String campo) {
        return no.get(campo).decimalValue();
    }

    /**
     * Confere que o indicador esta oculto, como o RF006 pede. Aceita tanto a chave faltando no JSON
     * quanto a chave presente com valor nulo, porque para a tela as duas coisas significam o mesmo:
     * nao tem o que mostrar. Assim o teste nao depende de o Jackson serializar nulo ou nao.
     */
    private static boolean ausente(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull();
    }

    /**
     * Quebra o vinculo de preco de um item que ja esta no diario, para montar o caso do item sem
     * preco.
     *
     * Faco por JPQL porque a API nao consegue gerar essa situacao, ja que o cadastro exige um item
     * de mercado. Na vida real isso acontece quando o vinculo se perde, e a regra e o total
     * diminuir com aviso em vez de contar R$ 0,00.
     */
    private void desvincularPrecoDoItem(String nomeItem) {
        entityManager
                .createQuery("update ItemRegistro i set i.itemMercado = null where i.nomeItem = :nome")
                .setParameter("nome", nomeItem)
                .executeUpdate();
        entityManager.clear();
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
            String token,
            String nome,
            String preco,
            String quantidade,
            String unidade,
            String categoria)
            throws Exception {
        String corpo =
                mockMvc.perform(
                                post("/itens-mercado")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                corpoItemMercado(
                                                        nome, preco, quantidade, unidade, categoria)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private void atualizarItemMercado(
            String token,
            long id,
            String nome,
            String preco,
            String quantidade,
            String unidade,
            String categoria)
            throws Exception {
        mockMvc.perform(
                        put("/itens-mercado/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        corpoItemMercado(nome, preco, quantidade, unidade, categoria)))
                .andExpect(status().isOk());
    }

    /** Categoria nula nao vai no JSON. E assim que o cadastro sem classificar o item e testado. */
    private static String corpoItemMercado(
            String nome, String preco, String quantidade, String unidade, String categoria) {
        String classificacao = categoria == null ? "" : ",\"categoria\":\"%s\"".formatted(categoria);

        return """
                {"nome":"%s","preco":%s,"quantidadeEmbalagem":%s,"unidade":"%s"%s}
                """
                .formatted(nome, preco, quantidade, unidade, classificacao);
    }

    private long criarRefeicao(String token, String corpoJson) throws Exception {
        String corpo =
                mockMvc.perform(
                                post("/refeicoes")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpoJson))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private void registrarNoDiario(String token, LocalDate data, long refeicaoId) throws Exception {
        mockMvc.perform(
                        post("/registros-diarios")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"data":"%s","refeicaoId":%d}
                                        """
                                                .formatted(data, refeicaoId)))
                .andExpect(status().isCreated());
    }

    private void definirMeta(String token, String valor, String periodo) throws Exception {
        mockMvc.perform(
                        put("/meta-orcamento")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"valor":%s,"periodo":"%s"}
                                        """
                                                .formatted(valor, periodo)))
                .andExpect(status().is2xxSuccessful());
    }
}
