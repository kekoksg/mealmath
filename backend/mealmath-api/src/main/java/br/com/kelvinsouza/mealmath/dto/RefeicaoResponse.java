package br.com.kelvinsouza.mealmath.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Refeicao modelo da biblioteca com o custo ja calculado (RF003/RF005).
 *
 * Aqui nao tem lista de itens sem preco, diferente do diario. Na biblioteca o vinculo com o item de
 * mercado e obrigatorio na propria coluna, entao essa situacao nem consegue acontecer. No diario
 * ela pode, porque la o vinculo pode se perder.
 */
public record RefeicaoResponse(
        Long id,
        String titulo,
        String icone,
        List<ItemRefeicaoResponse> itens,
        BigDecimal custoTotal) {}
