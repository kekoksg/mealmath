package br.com.kelvinsouza.mealmath.domain.exception;

import br.com.kelvinsouza.mealmath.domain.Grandeza;

/**
 * Mudar um item de KG para L estraga toda refeicao que o consome em gramas, porque o calculo
 * passaria a misturar massa com volume. O erro so apareceria bem depois, ao somar o dashboard,
 * entao o bloqueio acontece na hora da alteracao.
 */
public class ItemMercadoEmUsoException extends ConflitoException {

    public ItemMercadoEmUsoException(String nome, Grandeza atual, Grandeza nova) {
        super(
                ("Não é possível mudar \"%s\" de %s para %s: o item já é usado em refeições ou no "
                                + "diário, e a troca invalidaria as quantidades já registradas. "
                                + "Cadastre um item novo.")
                        .formatted(nome, atual, nova));
    }
}
