package br.com.kelvinsouza.mealmath.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Devolve o id do usuario da requisicao atual, que todo Service passa para os repositories.
 *
 * Usar isso em vez de aceitar um usuarioId vindo do corpo ou da URL e o que impede uma pessoa de
 * pedir os dados de outra so trocando um numero.
 */
@Component
public class UsuarioAutenticadoProvider {

    public Long idDoUsuarioAutenticado() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Requisição sem usuário autenticado.");
        }
        if (!(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticação atual não é um JWT desta API.");
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Token sem identificador de usuário válido.", e);
        }
    }
}
