package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.Categoria;
import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item de mercado com o custo unitario ja calculado (RF004).
 *
 * O custoUnitario e o custo de 1 g, 1 mL ou 1 un, conforme o campo unidadeBase, e vem sem
 * arredondar. Cuidado ao exibir com 2 casas: R$ 0,0189/g viraria R$ 0,02/g, o que da 6% de erro.
 */
public record ItemMercadoResponse(
        Long id,
        String nome,
        BigDecimal preco,
        BigDecimal quantidadeEmbalagem,
        UnidadeMedida unidade,
        BigDecimal custoUnitario,
        String unidadeBase,
        Categoria categoria,
        boolean ativo,
        Instant atualizadoEm) {

    public static ItemMercadoResponse de(ItemMercado item, BigDecimal custoUnitario) {
        return new ItemMercadoResponse(
                item.getId(),
                item.getNome(),
                item.getPreco(),
                item.getQuantidadeEmbalagem(),
                item.getUnidade(),
                custoUnitario,
                item.getUnidade().getGrandeza().getUnidadeBase(),
                item.getCategoria(),
                item.isAtivo(),
                item.getAtualizadoEm());
    }
}
