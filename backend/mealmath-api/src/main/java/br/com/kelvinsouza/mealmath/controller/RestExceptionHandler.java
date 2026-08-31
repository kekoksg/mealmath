package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.domain.exception.ConflitoException;
import br.com.kelvinsouza.mealmath.domain.exception.CredenciaisInvalidasException;
import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import br.com.kelvinsouza.mealmath.domain.exception.RegraNegocioException;
import br.com.kelvinsouza.mealmath.dto.ErroResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Transforma as excecoes de negocio em respostas HTTP, com a mensagem em pt-BR pronta para a tela.
 *
 * Como todas herdam de RegraNegocioException, uma regra nova nunca vaza como 500, que apagaria a
 * mensagem util e apareceria para o usuario como falha do servidor.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    /** Login errado: 401, sem separar e-mail que nao existe de senha errada. */
    @ExceptionHandler(CredenciaisInvalidasException.class)
    ResponseEntity<ErroResponse> tratarCredenciaisInvalidas(CredenciaisInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErroResponse.de(e.getMessage()));
    }

    /** Conflito com o que ja esta salvo (e-mail repetido, item duplicado, item em uso): 409. */
    @ExceptionHandler(ConflitoException.class)
    ResponseEntity<ErroResponse> tratarConflito(ConflitoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErroResponse.de(e.getMessage()));
    }

    /** Nao existe ou e de outro usuario. De fora os dois casos ficam iguais, de proposito. */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroResponse> tratarNaoEncontrado(RecursoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResponse.de(e.getMessage()));
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<ErroResponse> tratarRegraNegocio(RegraNegocioException e) {
        return ResponseEntity.badRequest().body(ErroResponse.de(e.getMessage()));
    }

    /** Erros do Bean Validation nos DTOs. Devolve campo por campo, para o formulario marcar certo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResponse> tratarValidacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErroResponse("Verifique os dados informados.", campos));
    }

    /**
     * JSON quebrado ou enum invalido, tipo mandar "unidade":"LITRO". Sem esse handler o Spring
     * devolve uma mensagem generica, e ai o erro mais comum do front, que e errar o nome da
     * unidade, chega sem dizer o que corrigir.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroResponse> tratarCorpoIlegivel(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(
                        ErroResponse.de(
                                "Corpo da requisição inválido. Confira os tipos dos campos —"
                                        + " unidade aceita apenas KG, G, L, ML ou UN."));
    }

    /**
     * Parametro da URL com tipo errado, tipo ?periodo=ANO ou ?data=ontem. Sem esse handler o erro
     * chega como 500 e parece problema do servidor, quando na verdade e a URL. Com ele o front
     * recebe 400 ja com a lista de valores aceitos.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErroResponse> tratarParametroInvalido(MethodArgumentTypeMismatchException e) {
        Class<?> tipo = e.getRequiredType();
        String aceitos =
                tipo != null && tipo.isEnum()
                        ? " Valores aceitos: %s."
                                .formatted(
                                        Arrays.stream(tipo.getEnumConstants())
                                                .map(String::valueOf)
                                                .collect(Collectors.joining(", ")))
                        : "";

        return ResponseEntity.badRequest()
                .body(
                        ErroResponse.de(
                                "Valor inválido para o parâmetro '%s'.%s".formatted(e.getName(), aceitos)));
    }
}
