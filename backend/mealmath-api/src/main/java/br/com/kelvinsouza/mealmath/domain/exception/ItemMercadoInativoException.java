package br.com.kelvinsouza.mealmath.domain.exception;

/**
 * O item desativado so continua no banco para o diario calcular o custo do passado; usa-lo numa
 * refeicao nova seria reativar sem querer.
 *
 * Itens que ja estavam na refeicao antes da edicao continuam aceitos, senao desativar um item
 * travaria a edicao de todas as refeicoes que o usam.
 */
public class ItemMercadoInativoException extends ConflitoException {

    public ItemMercadoInativoException(String nome) {
        super("O item \"%s\" está desativado e não pode ser adicionado. Cadastre-o novamente."
                .formatted(nome));
    }
}
