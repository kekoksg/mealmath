package br.com.kelvinsouza.mealmath.domain;

/**
 * Grandeza fisica da unidade de medida. So da para converter dentro da mesma grandeza: consumir
 * 150 g de um item vendido em litros e entrada invalida.
 */
public enum Grandeza {

    MASSA("g"),
    VOLUME("mL"),
    CONTAGEM("un");

    private final String unidadeBase;

    Grandeza(String unidadeBase) {
        this.unidadeBase = unidadeBase;
    }

    public String getUnidadeBase() {
        return unidadeBase;
    }
}
