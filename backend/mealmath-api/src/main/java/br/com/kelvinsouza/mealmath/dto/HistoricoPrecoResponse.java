package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.HistoricoPreco;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Um preco que ja valeu para o item (RF007).
 *
 * A embalagem e a unidade vao junto com o preco porque a comparacao certa e entre custos
 * unitarios. R$ 5,20 por 1 L virando R$ 5,60 por 2 L e queda de custo, mesmo com o preco subindo.
 */
public record HistoricoPrecoResponse(
        Long id,
        BigDecimal preco,
        BigDecimal quantidadeEmbalagem,
        UnidadeMedida unidade,
        BigDecimal custoUnitario,
        String unidadeBase,
        Instant substituidoEm) {

    public static HistoricoPrecoResponse de(HistoricoPreco historico, BigDecimal custoUnitario) {
        return new HistoricoPrecoResponse(
                historico.getId(),
                historico.getPreco(),
                historico.getQuantidadeEmbalagem(),
                historico.getUnidade(),
                custoUnitario,
                historico.getUnidade().getGrandeza().getUnidadeBase(),
                historico.getSubstituidoEm());
    }
}
