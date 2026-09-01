package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Entrada do botao de repetir as refeicoes de ontem (RF008).
 *
 * A data e o dia de destino. A origem e sempre o dia anterior a ela. O que e copiado sao os
 * registros do jeito que ficaram ontem, ja com os ajustes de quantidade, e nao os modelos da
 * biblioteca: repetir o dia e repetir o que foi comido de verdade.
 */
public record DuplicarDiaAnteriorRequest(
        @NotNull(message = "Informe a data de destino.") LocalDate data) {}
