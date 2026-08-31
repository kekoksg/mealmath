package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Cadastro e edicao de refeicao modelo da biblioteca (RF003).
 *
 * No PUT a lista de itens substitui a composicao inteira, nao e incremento: o que nao vier no
 * corpo e apagado da refeicao.
 */
public record RefeicaoRequest(
        @NotBlank(message = "Informe o título da refeição.")
        @Size(max = 120, message = "O título deve ter no máximo 120 caracteres.")
        String titulo,
        @Size(max = 60, message = "O ícone deve ter no máximo 60 caracteres.") String icone,
        @NotEmpty(message = "Adicione ao menos um item à refeição.")
        @Valid
        List<ItemRefeicaoRequest> itens) {}
