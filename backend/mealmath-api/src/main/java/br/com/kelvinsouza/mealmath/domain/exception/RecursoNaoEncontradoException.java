package br.com.kelvinsouza.mealmath.domain.exception;

/**
 * 404 tanto para o que nao existe quanto para o que e de outro usuario, de proposito. Um 403 no
 * dado alheio ja confirmaria que aquele id existe, e daria para contar os registros das outras
 * contas testando id por id.
 */
public class RecursoNaoEncontradoException extends RegraNegocioException {

    public RecursoNaoEncontradoException(String recurso) {
        super("%s não encontrado.".formatted(recurso));
    }
}
