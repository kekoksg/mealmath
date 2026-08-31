package br.com.kelvinsouza.mealmath.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracao do JWT, com prefixo app.jwt no application.properties.
 *
 * O segredo e a chave HMAC e precisa de pelo menos 32 bytes para o HS256. A validacao do construtor
 * derruba a aplicacao na subida, em vez de deixar passar token assinado com chave fraca.
 * O emissor vira a claim iss, que tambem e conferida quando o token volta.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String segredo,
        @DefaultValue("8h") Duration expiracao,
        @DefaultValue("mealmath-api") String emissor) {

    private static final int MINIMO_BYTES_HS256 = 32;

    public JwtProperties {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException(
                    "Defina app.jwt.segredo (ou a variável de ambiente APP_JWT_SEGREDO).");
        }
        int bytes = segredo.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MINIMO_BYTES_HS256) {
            throw new IllegalStateException(
                    "app.jwt.segredo precisa de no mínimo %d bytes para HS256 (atual: %d)."
                            .formatted(MINIMO_BYTES_HS256, bytes));
        }
        if (expiracao == null || expiracao.isZero() || expiracao.isNegative()) {
            throw new IllegalStateException("app.jwt.expiracao deve ser um período positivo.");
        }
    }
}
