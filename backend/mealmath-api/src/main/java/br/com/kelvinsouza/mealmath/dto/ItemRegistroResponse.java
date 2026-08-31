package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import java.math.BigDecimal;

/**
 * Item copiado para o diario, com id proprio. E esse id que o ajuste de quantidade usa.
 *
 * O custo vem nulo quando nao existe item de mercado vinculado. Nulo e nao zero, de proposito:
 * R$ 0,00 esconderia o custo real, e o certo e tirar do total e avisar na tela.
 */
public record ItemRegistroResponse(
        Long id,
        Long itemMercadoId,
        String nome,
        BigDecimal quantidadeConsumida,
        UnidadeMedida unidade,
        BigDecimal custo,
        boolean itemAtivo) {

    public static ItemRegistroResponse de(ItemRegistro item, BigDecimal custo) {
        boolean temVinculo = item.getItemMercado() != null;

        return new ItemRegistroResponse(
                item.getId(),
                temVinculo ? item.getItemMercado().getId() : null,
                item.getNomeItem(),
                item.getQuantidadeConsumida(),
                item.getUnidade(),
                custo,
                temVinculo && item.getItemMercado().isAtivo());
    }
}
