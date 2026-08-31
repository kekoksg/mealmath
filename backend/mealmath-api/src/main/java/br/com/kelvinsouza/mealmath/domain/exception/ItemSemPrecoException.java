package br.com.kelvinsouza.mealmath.domain.exception;

/**
 * Quem calcula uma refeicao inteira nao pode deixar essa excecao subir: o certo e tirar o item do
 * total e avisar o usuario. Contar como R$ 0,00 esconderia o custo real.
 */
public class ItemSemPrecoException extends RegraNegocioException {

    public ItemSemPrecoException(String descricaoItem) {
        super("O item \"%s\" não possui item de mercado vinculado; sem preço não há custo."
                .formatted(descricaoItem));
    }
}
