package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Ajuste da quantidade de um item so naquele dia (RF009).
 *
 * A alteracao mexe apenas na linha do registro diario. O modelo da biblioteca e os outros dias
 * continuam iguais.
 */
public record ItemRegistroQuantidadeRequest(
        @NotNull(message = "Informe a quantidade consumida.")
        @Positive(message = "A quantidade consumida deve ser maior que zero.")
        BigDecimal quantidadeConsumida,
        @NotNull(message = "Informe a unidade de consumo (KG, G, L, ML ou UN).")
        UnidadeMedida unidade) {}
