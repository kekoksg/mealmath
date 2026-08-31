package br.com.kelvinsouza.mealmath.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Dados de entrada do login (RF002).
 *
 * Sem @Email e sem @Size aqui, de proposito. No login, uma mensagem de formato daria uma
 * resposta diferente para entradas obviamente erradas e ajudaria quem esta tentando adivinhar.
 * Credencial errada devolve sempre o mesmo 401.
 */
public record LoginRequest(
        @NotBlank(message = "Informe seu e-mail.") String email,
        @NotBlank(message = "Informe sua senha.") String senha) {}
