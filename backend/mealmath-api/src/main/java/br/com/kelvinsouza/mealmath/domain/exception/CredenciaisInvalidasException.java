package br.com.kelvinsouza.mealmath.domain.exception;

/**
 * Falha no login (RF002). A mensagem e generica de proposito: separar "e-mail nao existe" de
 * "senha errada" deixaria qualquer um descobrir pela tela de login quais e-mails tem conta.
 */
public class CredenciaisInvalidasException extends RegraNegocioException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos.");
    }
}
