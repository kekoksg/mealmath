package br.com.kelvinsouza.mealmath.domain;

import java.math.BigDecimal;

/**
 * As tres informacoes que o calculo do custo precisa: preco, quantidade da embalagem e unidade.
 *
 * Andam sempre juntas porque comparar so o preco da errado quando o tamanho da embalagem muda:
 * R$ 5,20 o litro virando R$ 5,60 os 2 litros e queda de custo, nao alta.
 *
 * Existe porque a biblioteca e o diario respondem isso de formas diferentes: o ItemRefeicao devolve
 * o preco atual do mercado e o ItemRegistro, o preco congelado no dia do consumo.
 */
public record BasePreco(BigDecimal preco, BigDecimal quantidadeEmbalagem, UnidadeMedida unidade) {

    /** Monta a base com o preco que o item de mercado tem hoje. */
    public static BasePreco vigenteDe(ItemMercado item) {
        return item == null
                ? null
                : new BasePreco(item.getPreco(), item.getQuantidadeEmbalagem(), item.getUnidade());
    }
}
