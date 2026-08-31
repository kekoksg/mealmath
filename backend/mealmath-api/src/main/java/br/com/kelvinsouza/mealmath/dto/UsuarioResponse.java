package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.Usuario;
import java.time.Instant;

/**
 * Dados do usuario que a API pode devolver. O hash da senha nunca sai daqui.
 * O campo criadoEm e o "Membro desde" que aparece na tela de perfil.
 */
public record UsuarioResponse(Long id, String nome, String email, Instant criadoEm) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCriadoEm());
    }
}
