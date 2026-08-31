package br.com.kelvinsouza.mealmath.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Formato padrao de erro da API. A mensagem ja vem em pt-BR, pronta para mostrar na tela.
 * O mapa de campos so aparece no JSON quando o erro e de validacao de formulario.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(String mensagem, Map<String, String> campos) {

    public static ErroResponse de(String mensagem) {
        return new ErroResponse(mensagem, null);
    }
}
