package br.com.kelvinsouza.mealmath.domain;

import java.math.BigDecimal;

/**
 * Unidades aceitas na compra e no consumo.
 *
 * O fatorParaBase e so o dado da conversao: quantos g, mL ou un cabem em 1 unidade dessa aqui.
 * A conta da conversao e do rateio fica na camada de Service.
 */
public enum UnidadeMedida {

    KG(Grandeza.MASSA, new BigDecimal("1000")),
    G(Grandeza.MASSA, BigDecimal.ONE),
    L(Grandeza.VOLUME, new BigDecimal("1000")),
    ML(Grandeza.VOLUME, BigDecimal.ONE),
    UN(Grandeza.CONTAGEM, BigDecimal.ONE);

    private final Grandeza grandeza;
    private final BigDecimal fatorParaBase;

    UnidadeMedida(Grandeza grandeza, BigDecimal fatorParaBase) {
        this.grandeza = grandeza;
        this.fatorParaBase = fatorParaBase;
    }

    public Grandeza getGrandeza() {
        return grandeza;
    }

    public BigDecimal getFatorParaBase() {
        return fatorParaBase;
    }

    /** Duas unidades so podem ser comparadas ou convertidas se forem da mesma grandeza. */
    public boolean compativelCom(UnidadeMedida outra) {
        return outra != null && this.grandeza == outra.grandeza;
    }
}
