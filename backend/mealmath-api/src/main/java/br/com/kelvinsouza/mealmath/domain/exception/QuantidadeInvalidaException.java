package br.com.kelvinsouza.mealmath.domain.exception;

import java.math.BigDecimal;

/**
 * Existe para a divisao por zero aparecer como erro de validacao com nome do campo, e nao como
 * ArithmeticException vinda do meio da conta.
 */
public class QuantidadeInvalidaException extends RegraNegocioException {

    public QuantidadeInvalidaException(String campo, BigDecimal valor) {
        super("%s deve ser maior que zero (recebido: %s).".formatted(campo, valor));
    }
}
