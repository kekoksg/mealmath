package br.com.kelvinsouza.mealmath.domain.exception;

/** 409 e nao 400: os dados enviados estao certos, o problema e o que ja existe no banco. */
public class ConflitoException extends RegraNegocioException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
