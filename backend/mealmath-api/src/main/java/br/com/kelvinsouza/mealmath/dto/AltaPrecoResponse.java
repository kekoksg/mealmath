package br.com.kelvinsouza.mealmath.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item que ficou mais caro na ultima troca de preco (RF006 e RF007).
 *
 * A variacao e medida no custo unitario e nao no preco da etiqueta. Os dois so batem quando a
 * embalagem continua do mesmo tamanho: R$ 5,20 por 1 L virando R$ 5,60 por 2 L e um preco 8%
 * maior mas um custo por mL 46% menor. Alertando pela etiqueta, o sistema chamaria essa queda de
 * alta.
 *
 * Por isso precoAnterior e precoAtual servem so para o usuario reconhecer a compra no texto
 * "R$ 16,50 -> R$ 18,90". A conta usa os custos unitarios.
 */
public record AltaPrecoResponse(
        Long itemMercadoId,
        String nome,
        BigDecimal precoAnterior,
        BigDecimal precoAtual,
        BigDecimal custoUnitarioAnterior,
        BigDecimal custoUnitarioAtual,
        String unidadeBase,
        BigDecimal variacaoPercentual,
        Instant alteradoEm) {}
