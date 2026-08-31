package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Um item da refeicao modelo: qual item de mercado e quanto dele (RF003).
 *
 * A unidade aqui e a do consumo, que pode ser diferente da embalagem desde que seja da mesma
 * grandeza. Comprar em KG e consumir em G e o caso mais comum. Quem confere isso e o Service.
 */
public record ItemRefeicaoRequest(
        @NotNull(message = "Informe o item de mercado.") Long itemMercadoId,
        @NotNull(message = "Informe a quantidade consumida.")
        @Positive(message = "A quantidade consumida deve ser maior que zero.")
        BigDecimal quantidadeConsumida,
        @NotNull(message = "Informe a unidade de consumo (KG, G, L, ML ou UN).")
        UnidadeMedida unidade) {}
