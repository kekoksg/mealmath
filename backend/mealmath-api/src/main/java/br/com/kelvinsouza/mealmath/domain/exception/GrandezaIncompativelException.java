package br.com.kelvinsouza.mealmath.domain.exception;

import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;

/** Nao existe fator de conversao entre massa e volume: e entrada invalida, nao conta a tentar. */
public class GrandezaIncompativelException extends RegraNegocioException {

    public GrandezaIncompativelException(UnidadeMedida embalagem, UnidadeMedida consumo) {
        super(
                "Grandezas incompatíveis: item vendido em %s (%s) não pode ser consumido em %s (%s)."
                        .formatted(
                                embalagem,
                                embalagem.getGrandeza(),
                                consumo,
                                consumo.getGrandeza()));
    }
}
