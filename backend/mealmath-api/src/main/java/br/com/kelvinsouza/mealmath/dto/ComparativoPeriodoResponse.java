package br.com.kelvinsouza.mealmath.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comparacao com o periodo anterior, de mesma duracao (RF006).
 *
 * A variacaoPercentual ja vem com 2 casas, porque e numero final de leitura. Positivo quer dizer
 * que o usuario gastou mais que antes.
 */
public record ComparativoPeriodoResponse(
        LocalDate inicio, LocalDate fim, BigDecimal custoTotal, BigDecimal variacaoPercentual) {}
