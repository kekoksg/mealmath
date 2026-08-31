package br.com.kelvinsouza.mealmath.security;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtProperties — a aplicação não sobe com chave fraca")
class JwtPropertiesTest {

    private static final String SEGREDO_VALIDO = "segredo-de-desenvolvimento-com-32-bytes-ou-mais";

    @Test
    @DisplayName("Segredo com menos de 32 bytes é recusado na subida (HS256 exige 256 bits)")
    void recusaSegredoCurto() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtProperties("curto-demais", Duration.ofHours(8), "api"))
                .withMessageContaining("32 bytes");
    }

    @Test
    void recusaSegredoAusente() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtProperties(null, Duration.ofHours(8), "api"));
    }

    @Test
    void recusaSegredoEmBranco() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtProperties("   ", Duration.ofHours(8), "api"));
    }

    @Test
    void recusaExpiracaoNaoPositiva() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtProperties(SEGREDO_VALIDO, Duration.ZERO, "api"));
    }

    @Test
    void aceitaConfiguracaoValida() {
        assertThatNoException()
                .isThrownBy(() -> new JwtProperties(SEGREDO_VALIDO, Duration.ofHours(8), "api"));
    }
}
