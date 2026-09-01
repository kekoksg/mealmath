package br.com.kelvinsouza.mealmath.security;

import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Separa, no log, "esse id nao existe" de "esse id existe e e de outra conta".
 *
 * A API responde 404 nos dois casos de proposito (ver RecursoNaoEncontradoException): um 403 no dado
 * alheio ja confirmaria que aquele id existe. Só que, respondendo igual e sem registrar nada, a
 * tentativa de ler dado de outra conta ficava invisivel tambem para quem opera a aplicacao. Aqui a
 * diferenca volta a existir, mas so para dentro: a resposta HTTP continua identica nos dois casos.
 *
 * Custo: a consulta que confere o dono e passada como BooleanSupplier e so e avaliada no caminho de
 * erro, depois de a busca escopada por usuario ja ter voltado vazia. Requisicao que acha o recurso
 * nao paga consulta nenhuma a mais.
 *
 * O log leva apenas ids. Nada de e-mail, nome ou senha: quem for investigar cruza os ids com o
 * banco, e ai o registro de seguranca nao vira mais um lugar por onde dado pessoal vaza.
 */
@Component
public class AuditoriaDeAcesso {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaDeAcesso.class);

    /**
     * Devolve o recurso que a busca escopada por usuario achou. Quando nao achou, decide entre ruido
     * e sinal de seguranca antes de lancar o 404.
     *
     * @param encontrado resultado da busca ja filtrada pelo usuario autenticado
     * @param tipoRecurso nome do recurso em pt-BR, o mesmo que vai na mensagem do 404
     * @param recursoId id pedido na URL ou no corpo da requisicao
     * @param usuarioId id do usuario do token, nunca o e-mail nem o nome
     * @param existeDeOutroUsuario confere se o id existe em outra conta; so e avaliado quando
     *     {@code encontrado} vem vazio
     */
    public <T> T exigirDoUsuario(
            Optional<T> encontrado,
            String tipoRecurso,
            Long recursoId,
            Long usuarioId,
            BooleanSupplier existeDeOutroUsuario) {

        return encontrado.orElseThrow(
                () -> naoEncontrado(tipoRecurso, recursoId, usuarioId, existeDeOutroUsuario));
    }

    /** Mesma decisao para as buscas que respondem com boolean em vez de devolver a entidade. */
    public void exigirDoUsuario(
            boolean encontrado,
            String tipoRecurso,
            Long recursoId,
            Long usuarioId,
            BooleanSupplier existeDeOutroUsuario) {

        if (!encontrado) {
            throw naoEncontrado(tipoRecurso, recursoId, usuarioId, existeDeOutroUsuario);
        }
    }

    private RecursoNaoEncontradoException naoEncontrado(
            String tipoRecurso,
            Long recursoId,
            Long usuarioId,
            BooleanSupplier existeDeOutroUsuario) {

        if (existeDeOutroUsuario.getAsBoolean()) {
            log.warn(
                    "Acesso negado a recurso de outra conta:"
                            + " tipoRecurso=\"{}\" recursoId={} usuarioId={}",
                    tipoRecurso,
                    recursoId,
                    usuarioId);
        } else {
            // DEBUG de proposito. Pedir um id que nao existe e erro comum de link velho e de tela
            // que guardou um id ja apagado; em WARN esse volume afogaria o caso de cima, que e o
            // unico que vale investigar.
            log.debug(
                    "Recurso inexistente: tipoRecurso=\"{}\" recursoId={} usuarioId={}",
                    tipoRecurso,
                    recursoId,
                    usuarioId);
        }

        return new RecursoNaoEncontradoException(tipoRecurso);
    }
}
