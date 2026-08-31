package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada do cadastro (RF001).
 *
 * O limite de 72 caracteres na senha nao e enfeite: o bcrypt ignora o que passa do byte 72 e o
 * BCryptPasswordEncoder do Spring recusa entradas maiores. Validando aqui isso vira um 400 claro
 * em vez de estourar no meio do cadastro.
 */
public record CadastroRequest(
        @NotBlank(message = "Informe seu nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,
        @NotBlank(message = "Informe seu e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
        String email,
        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String senha) {}
