package br.com.kelvinsouza.mealmath.domain.exception;

/**
 * 400 e nao 401: o token e valido e a sessao nao expirou. Com 401 o front deslogaria o usuario em
 * vez de so marcar o campo errado do formulario.
 */
public class SenhaAtualIncorretaException extends RegraNegocioException {

    public SenhaAtualIncorretaException() {
        super("Senha atual incorreta.");
    }
}
