package br.com.kelvinsouza.mealmath.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.exception.GrandezaIncompativelException;
import br.com.kelvinsouza.mealmath.domain.exception.ItemSemPrecoException;
import br.com.kelvinsouza.mealmath.domain.exception.QuantidadeInvalidaException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Aqui o usuario dono das entidades fica nulo de proposito. Sao testes de unidade so da conta do
 * custo, sem banco. O filtro por usuario e responsabilidade dos repositories.
 */
@DisplayName("CalculadoraCustoService — rateio fracionado (RF005)")
class CalculadoraCustoServiceTest {

    private final CalculadoraCustoService calculadora =
            new CalculadoraCustoService(new ConversorUnidadeService());

    private static ItemMercado itemMercado(
            String nome, String preco, String quantidade, UnidadeMedida unidade) {
        return new ItemMercado(
                null, nome, new BigDecimal(preco), new BigDecimal(quantidade), unidade);
    }

    private static ItemRefeicao consumo(ItemMercado item, String quantidade, UnidadeMedida unidade) {
        return new ItemRefeicao(item, new BigDecimal(quantidade), unidade);
    }

    @Nested
    @DisplayName("Casos canônicos da tabela de conversão")
    class CasosCanonicos {

        @ParameterizedTest(name = "{0}: R$ {1} / {2} {3} → {4} {5} = R$ {6}")
        @CsvSource({
            "Frango,   18.90, 1,   KG, 150, G,  2.835",
            "Aveia,     9.50, 500, G,   40, G,  0.76",
            "Leite,     5.20, 1,   L,  200, ML, 1.04",
            "Ovos,     12.00, 12,  UN,   2, UN, 2.00",
            "Brócolis,  4.50, 1,   UN, 0.5, UN, 2.25"
        })
        void calculaOCustoDaFracaoConsumida(
                String nome,
                String preco,
                String qtdEmbalagem,
                UnidadeMedida unidadeEmbalagem,
                String qtdConsumida,
                UnidadeMedida unidadeConsumo,
                BigDecimal custoEsperado) {

            ItemMercado item = itemMercado(nome, preco, qtdEmbalagem, unidadeEmbalagem);

            BigDecimal custo = calculadora.custoItem(consumo(item, qtdConsumida, unidadeConsumo));

            assertThat(custo).isEqualByComparingTo(custoEsperado);
        }

        @Test
        @DisplayName("Custo unitário do frango: R$ 18,90 / 1 kg = R$ 0,0189 por grama")
        void calculaCustoUnitarioNaUnidadeBase() {
            ItemMercado frango = itemMercado("Frango", "18.90", "1", UnidadeMedida.KG);

            assertThat(calculadora.custoUnitario(frango))
                    .isEqualByComparingTo(new BigDecimal("0.0189"));
        }

        @Test
        @DisplayName("Embalagem em g é tão válida quanto em kg: mesmo preço/quantidade, mesmo custo")
        void embalagemNaUnidadeMenorProduzOMesmoCusto() {
            ItemMercado emKg = itemMercado("Frango", "18.90", "1", UnidadeMedida.KG);
            ItemMercado emG = itemMercado("Frango", "18.90", "1000", UnidadeMedida.G);

            assertThat(calculadora.custoItem(consumo(emG, "150", UnidadeMedida.G)))
                    .isEqualByComparingTo(calculadora.custoItem(consumo(emKg, "150", UnidadeMedida.G)));
        }

        @Test
        @DisplayName("A refeição é a soma dos cinco casos canônicos: R$ 8,885")
        void somaOCustoDeTodosOsItensDaRefeicao() {
            Refeicao refeicao = new Refeicao(null, "Refeição canônica", "prato");
            refeicao.adicionarItem(
                    consumo(itemMercado("Frango", "18.90", "1", UnidadeMedida.KG), "150", UnidadeMedida.G));
            refeicao.adicionarItem(
                    consumo(itemMercado("Aveia", "9.50", "500", UnidadeMedida.G), "40", UnidadeMedida.G));
            refeicao.adicionarItem(
                    consumo(itemMercado("Leite", "5.20", "1", UnidadeMedida.L), "200", UnidadeMedida.ML));
            refeicao.adicionarItem(
                    consumo(itemMercado("Ovos", "12.00", "12", UnidadeMedida.UN), "2", UnidadeMedida.UN));
            refeicao.adicionarItem(
                    consumo(itemMercado("Brócolis", "4.50", "1", UnidadeMedida.UN), "0.5", UnidadeMedida.UN));

            ResultadoCusto resultado = calculadora.calcularRefeicao(refeicao);

            // 2,835 + 0,76 + 1,04 + 2,00 + 2,25
            assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("8.885"));
            assertThat(resultado.itens()).hasSize(5);
            assertThat(resultado.possuiItensSemPreco()).isFalse();
        }
    }

    @Nested
    @DisplayName("Caso de borda: grandezas incompatíveis")
    class GrandezasIncompativeis {

        @Test
        @DisplayName("Consumir em g um item vendido em L é rejeitado, não convertido")
        void rejeitaConsumoEmMassaDeItemVendidoEmVolume() {
            ItemMercado leite = itemMercado("Leite", "5.20", "1", UnidadeMedida.L);

            assertThatExceptionOfType(GrandezaIncompativelException.class)
                    .isThrownBy(() -> calculadora.custoItem(consumo(leite, "200", UnidadeMedida.G)));
        }

        @Test
        @DisplayName("Consumir em un um item vendido em kg é rejeitado")
        void rejeitaConsumoEmContagemDeItemVendidoEmMassa() {
            ItemMercado frango = itemMercado("Frango", "18.90", "1", UnidadeMedida.KG);

            assertThatExceptionOfType(GrandezaIncompativelException.class)
                    .isThrownBy(() -> calculadora.custoItem(consumo(frango, "2", UnidadeMedida.UN)))
                    .withMessageContaining("MASSA")
                    .withMessageContaining("CONTAGEM");
        }
    }

    @Nested
    @DisplayName("Caso de borda: quantidade ou preço zero")
    class DivisaoPorZero {

        @Test
        @DisplayName("Embalagem zero vira erro de validação, não ArithmeticException")
        void rejeitaEmbalagemZerada() {
            ItemMercado item = itemMercado("Item quebrado", "10.00", "0", UnidadeMedida.G);

            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> calculadora.custoUnitario(item))
                    .withMessageContaining("Quantidade da embalagem");
        }

        @Test
        void rejeitaPrecoZerado() {
            ItemMercado item = itemMercado("Item quebrado", "0.00", "500", UnidadeMedida.G);

            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> calculadora.custoUnitario(item))
                    .withMessageContaining("Preço");
        }

        @Test
        void rejeitaPrecoNegativo() {
            ItemMercado item = itemMercado("Item quebrado", "-5.00", "500", UnidadeMedida.G);

            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> calculadora.custoUnitario(item));
        }

        @Test
        void rejeitaQuantidadeConsumidaZerada() {
            ItemMercado aveia = itemMercado("Aveia", "9.50", "500", UnidadeMedida.G);

            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> calculadora.custoItem(consumo(aveia, "0", UnidadeMedida.G)))
                    .withMessageContaining("Quantidade consumida");
        }

        @Test
        void rejeitaQuantidadeConsumidaNegativa() {
            ItemMercado aveia = itemMercado("Aveia", "9.50", "500", UnidadeMedida.G);

            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> calculadora.custoItem(consumo(aveia, "-40", UnidadeMedida.G)));
        }
    }

    @Nested
    @DisplayName("Caso de borda: item sem preço vinculado")
    class ItemSemPreco {

        private ItemRegistro semVinculo(String nome) {
            return new ItemRegistro(null, nome, new BigDecimal("50"), UnidadeMedida.G);
        }

        @Test
        @DisplayName("Calcular um item sem vínculo isoladamente falha explicitamente")
        void custoDeItemIsoladoSemPrecoFalha() {
            assertThatExceptionOfType(ItemSemPrecoException.class)
                    .isThrownBy(() -> calculadora.custoItem(semVinculo("Azeite")))
                    .withMessageContaining("Azeite");
        }

        @Test
        @DisplayName("Na refeição, o item sai do total e é sinalizado — nunca entra como R$ 0,00")
        void itemSemPrecoEExcluidoDoTotalESinalizado() {
            ItemMercado frango = itemMercado("Frango", "18.90", "1", UnidadeMedida.KG);
            ItemRegistro comPreco =
                    new ItemRegistro(frango, "Frango", new BigDecimal("150"), UnidadeMedida.G);

            ResultadoCusto resultado = calculadora.calcular(List.of(comPreco, semVinculo("Azeite")));

            assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("2.835"));
            assertThat(resultado.itens()).hasSize(1);
            assertThat(resultado.itensSemPreco()).containsExactly("Azeite");
            assertThat(resultado.possuiItensSemPreco()).isTrue();
        }

        @Test
        @DisplayName("Refeição inteira sem preço: total zero, mas com todos os itens sinalizados")
        void totalZeradoVemAcompanhadoDeSinalizacao() {
            ResultadoCusto resultado =
                    calculadora.calcular(List.of(semVinculo("Azeite"), semVinculo("Sal")));

            assertThat(resultado.total()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.itens()).isEmpty();
            assertThat(resultado.itensSemPreco()).containsExactly("Azeite", "Sal");
        }
    }

    @Nested
    @DisplayName("Caso de borda: arredondamento")
    class Arredondamento {

        @Test
        @DisplayName("Não arredonda valores intermediários: 3 × (R$ 1,00 / 3 un) fecha em R$ 1,00")
        void somaComEscalaCheiaAntesDeArredondar() {
            ItemMercado item = itemMercado("Item terço", "1.00", "3", UnidadeMedida.UN);
            Refeicao refeicao = new Refeicao(null, "Três terços", "prato");
            refeicao.adicionarItem(consumo(item, "1", UnidadeMedida.UN));
            refeicao.adicionarItem(consumo(item, "1", UnidadeMedida.UN));
            refeicao.adicionarItem(consumo(item, "1", UnidadeMedida.UN));

            BigDecimal total = calculadora.calcularRefeicao(refeicao).total();

            // Arredondando item por item daria 0,33 x 3 = R$ 0,99, tres centavos a menos.
            assertThat(total.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("O total preserva a escala cheia: R$ 2,835 não vira R$ 2,84 no service")
        void naoArredondaNaCamadaDeServico() {
            ItemMercado frango = itemMercado("Frango", "18.90", "1", UnidadeMedida.KG);
            Refeicao refeicao = new Refeicao(null, "Almoço", "prato");
            refeicao.adicionarItem(consumo(frango, "150", UnidadeMedida.G));

            assertThat(calculadora.calcularRefeicao(refeicao).total())
                    .isEqualByComparingTo(new BigDecimal("2.835"));
        }
    }

    @Nested
    @DisplayName("Refeição vazia")
    class RefeicaoVazia {

        @Test
        void refeicaoSemItensCustaZeroSemFalhar() {
            ResultadoCusto resultado = calculadora.calcularRefeicao(new Refeicao(null, "Vazia", null));

            assertThat(resultado.total()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.itens()).isEmpty();
            assertThat(resultado.possuiItensSemPreco()).isFalse();
        }
    }
}
