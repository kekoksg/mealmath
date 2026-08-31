package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Registra que uma refeicao da biblioteca foi consumida numa data (RF009).
 *
 * O corpo aponta para o modelo, mas o que e gravado e uma copia dele: titulo, icone e itens passam
 * a viver dentro do registro. Ajustar o registro depois nao mexe na biblioteca.
 *
 * A data vai sem hora e sem fuso, para a refeicao nao mudar de dia dependendo do fuso do navegador.
 */
public record RegistroDiarioRequest(
        @NotNull(message = "Informe a data do consumo.") LocalDate data,
        @NotNull(message = "Informe a refeição da biblioteca.") Long refeicaoId) {}
