package br.com.kelvinsouza.mealmath.domain.exception;

/** RF001, fluxo de excecao. A mensagem so orienta a fazer login, sem revelar nada da conta. */
public class EmailJaCadastradoException extends ConflitoException {

    public EmailJaCadastradoException() {
        super("Este e-mail já está cadastrado. Faça login em vez de criar uma nova conta.");
    }
}
