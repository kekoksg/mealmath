package br.com.kelvinsouza.mealmath.domain.exception;

/**
 * RF004, fluxo de excecao. A ideia e o usuario atualizar o preco do item existente (RF007): dois
 * "Frango" no banco dividem o custo em duas linhas e atrapalham o historico de preco.
 */
public class ItemMercadoDuplicadoException extends ConflitoException {

    public ItemMercadoDuplicadoException(String nome) {
        super("Você já cadastrou um item chamado \"%s\". Atualize o preço dele em vez de duplicar."
                .formatted(nome));
    }
}
