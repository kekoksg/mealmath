package br.com.kelvinsouza.mealmath.domain;

import java.math.BigDecimal;

/**
 * O que ItemRefeicao (biblioteca) e ItemRegistro (diario) tem em comum: tanto deste item de
 * mercado, nesta unidade. Existe para o calculo de custo ser escrito uma vez so; as duas seguem
 * sendo tabelas separadas, sem referencia uma para a outra.
 */
public interface ItemConsumido {

    /** De onde sai o preco. Pode ser nulo no diario, quando o item perdeu o vinculo. */
    ItemMercado getItemMercado();

        /**
         * Nula quando nao existe preco: ai o item sai do total e e sinalizado na tela, nunca vira
         * R$ 0,00.
         *
         * E aqui que biblioteca e diario divergem — uma devolve o preco atual, o outro o congelado — e
         * quem faz a conta nao precisa saber qual dos dois recebeu.
         */
    BasePreco getBasePreco();

    BigDecimal getQuantidadeConsumida();

    UnidadeMedida getUnidade();

    /** Nome do item, usado nas mensagens de erro e no detalhamento do custo. */
    String getDescricao();
}
