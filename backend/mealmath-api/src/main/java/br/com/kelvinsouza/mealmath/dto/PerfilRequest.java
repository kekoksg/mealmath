package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dados da edicao de perfil. A senha nao entra aqui, a troca de senha tem rota separada. */
public record PerfilRequest(
        @NotBlank(message = "Informe seu nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,
        @NotBlank(message = "Informe seu e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
        String email) {}
