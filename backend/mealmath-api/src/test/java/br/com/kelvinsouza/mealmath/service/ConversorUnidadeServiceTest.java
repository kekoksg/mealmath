package br.com.kelvinsouza.mealmath.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.exception.GrandezaIncompativelException;
import br.com.kelvinsouza.mealmath.domain.exception.QuantidadeInvalidaException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ConversorUnidadeService — normalização massa→g, volume→mL, contagem→un")
class ConversorUnidadeServiceTest {

    private final ConversorUnidadeService conversor = new ConversorUnidadeService();

    @Nested
    @DisplayName("Normalização para a unidade base")
    class Normalizacao {

        @ParameterizedTest(name = "{0} {1} → {2} na base")
        @CsvSource({
            "1,    KG, 1000",
            "1.5,  KG, 1500",
            "500,  G,  500",
            "1,    L,  1000",
            "200,  ML, 200",
            "12,   UN, 12",
            "0.5,  UN, 0.5"
        })
        void converteParaAUnidadeBaseDaGrandeza(
                BigDecimal quantidade, UnidadeMedida unidade, BigDecimal esperado) {
            assertThat(conversor.paraBase(quantidade, unidade)).isEqualByComparingTo(esperado);
        }

        @Test
        @DisplayName("1 kg e 1000 g são o mesmo valor após normalizar")
        void mesmaQuantidadeEmUnidadesDiferentesNormalizaIgual() {
            BigDecimal emKg = conversor.paraBase(BigDecimal.ONE, UnidadeMedida.KG);
            BigDecimal emG = conversor.paraBase(new BigDecimal("1000"), UnidadeMedida.G);

            assertThat(emKg).isEqualByComparingTo(emG);
        }

        @Test
        @DisplayName("1 L e 1000 mL são o mesmo valor após normalizar")
        void volumeNormalizaIgual() {
            BigDecimal emL = conversor.paraBase(BigDecimal.ONE, UnidadeMedida.L);
            BigDecimal emMl = conversor.paraBase(new BigDecimal("1000"), UnidadeMedida.ML);

            assertThat(emL).isEqualByComparingTo(emMl);
        }

        @Test
        @DisplayName("Não arredonda a fração: 0,5 un permanece 0,5")
        void naoArredondaFracaoDeUnidade() {
            assertThat(conversor.paraBase(new BigDecimal("0.5"), UnidadeMedida.UN))
                    .isEqualByComparingTo(new BigDecimal("0.5"));
        }
    }

    @Nested
    @DisplayName("Caso de borda: grandezas incompatíveis")
    class GrandezasIncompativeis {

        @ParameterizedTest(name = "embalagem em {0} + consumo em {1} é entrada inválida")
        @CsvSource({"L, G", "KG, ML", "G, UN", "UN, ML", "KG, UN"})
        void rejeitaConversaoEntreGrandezasDiferentes(
                UnidadeMedida embalagem, UnidadeMedida consumo) {
            assertThatExceptionOfType(GrandezaIncompativelException.class)
                    .isThrownBy(() -> conversor.exigirMesmaGrandeza(embalagem, consumo));
        }

        @ParameterizedTest(name = "embalagem em {0} + consumo em {1} é combinação válida")
        @CsvSource({"KG, G", "G, KG", "G, G", "L, ML", "ML, L", "UN, UN"})
        void aceitaConversaoDentroDaMesmaGrandeza(
                UnidadeMedida embalagem, UnidadeMedida consumo) {
            conversor.exigirMesmaGrandeza(embalagem, consumo);
        }
    }

    @Nested
    @DisplayName("Caso de borda: quantidade zero ou negativa")
    class QuantidadeInvalida {

        @ParameterizedTest(name = "quantidade {0} é rejeitada")
        @CsvSource({"0", "0.00", "-1", "-0.001"})
        void rejeitaQuantidadeNaoPositiva(BigDecimal quantidade) {
            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> conversor.paraBase(quantidade, UnidadeMedida.G));
        }

        @Test
        void rejeitaQuantidadeNula() {
            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(() -> conversor.paraBase(null, UnidadeMedida.G));
        }

        @Test
        void rejeitaUnidadeNula() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> conversor.paraBase(BigDecimal.ONE, null));
        }

        @Test
        @DisplayName("A mensagem nomeia o campo, para a validação chegar útil ao usuário")
        void mensagemIdentificaOCampo() {
            assertThatExceptionOfType(QuantidadeInvalidaException.class)
                    .isThrownBy(
                            () ->
                                    conversor.paraBase(
                                            BigDecimal.ZERO,
                                            UnidadeMedida.G,
                                            "Quantidade da embalagem"))
                    .withMessageContaining("Quantidade da embalagem");
        }
    }
}
