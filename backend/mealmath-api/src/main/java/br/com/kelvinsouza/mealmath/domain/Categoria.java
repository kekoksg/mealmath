package br.com.kelvinsouza.mealmath.domain;

/**
 * Categoria do item de mercado, usada no grafico de composicao do gasto no dashboard (RF006).
 * Nao entra em calculo nenhum, serve so para agrupar na hora de mostrar.
 *
 * OUTROS e o padrao de quem nao classificou o item. Sem ele o item ficaria de fora do grafico e a
 * soma das fatias nao bateria com o custo total mostrado no topo da tela.
 */
public enum Categoria {

    PROTEINA("Proteína"),
    HORTIFRUTI("Hortifruti"),
    CARBOIDRATO("Carboidrato"),
    LATICINIO("Laticínio"),
    OUTROS("Outros");

    private final String rotulo;

    Categoria(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Nome ja acentuado para a legenda do grafico, assim o front nao precisa traduzir. */
    public String getRotulo() {
        return rotulo;
    }
}
