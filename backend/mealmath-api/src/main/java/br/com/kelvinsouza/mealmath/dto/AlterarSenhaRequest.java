package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Troca de senha.
 *
 * Peco a senha atual mesmo com o usuario ja logado, para quem pegar um token vazado ou um celular
 * destravado nao conseguir tomar a conta so trocando a senha. O limite de 72 e o mesmo do cadastro,
 * porque o bcrypt ignora o que passa disso.
 */
public record AlterarSenhaRequest(
        @NotBlank(message = "Informe sua senha atual.") String senhaAtual,
        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String novaSenha) {}
