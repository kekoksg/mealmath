package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import java.math.BigDecimal;

/**
 * Item da refeicao com o custo da porcao ja calculado (RF005).
 *
 * O custo vem sem arredondar, porque a soma do total e feita antes de qualquer arredondamento.
 *
 * O campo itemAtivo em false quer dizer que o item de mercado foi desativado. A refeicao ainda
 * consegue ser calculada, mas a tela precisa avisar antes do usuario tentar editar.
 */
public record ItemRefeicaoResponse(
        Long id,
        Long itemMercadoId,
        String nome,
        BigDecimal quantidadeConsumida,
        UnidadeMedida unidade,
        BigDecimal custo,
        boolean itemAtivo) {

    public static ItemRefeicaoResponse de(ItemRefeicao item, BigDecimal custo) {
        return new ItemRefeicaoResponse(
                item.getId(),
                item.getItemMercado().getId(),
                item.getItemMercado().getNome(),
                item.getQuantidadeConsumida(),
                item.getUnidade(),
                custo,
                item.getItemMercado().isAtivo());
    }
}
