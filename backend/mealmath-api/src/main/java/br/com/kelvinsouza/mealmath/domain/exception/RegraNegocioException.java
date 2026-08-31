package br.com.kelvinsouza.mealmath.domain.exception;

/** Base das regras de negocio, para o ControllerAdvice mapear tudo para 400 em um lugar so. */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
