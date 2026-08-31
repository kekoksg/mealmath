package br.com.kelvinsouza.mealmath.security;

import br.com.kelvinsouza.mealmath.domain.Usuario;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Faz a ponte entre a entidade Usuario e o Spring Security, levando o id junto. E esse id que vira
 * o sub do JWT e depois filtra todas as consultas.
 *
 * Nao tem papel ou permissao porque o sistema so tem um tipo de usuario, e o
 * anyRequest().authenticated() nao precisa de nenhuma authority.
 */
public class UsuarioAutenticado implements UserDetails {

    private final Long id;
    private final String email;
    private final String nome;
    private final String senhaHash;
    private final Instant criadoEm;

    public UsuarioAutenticado(Usuario usuario) {
        this.id = usuario.getId();
        this.email = usuario.getEmail();
        this.nome = usuario.getNome();
        this.senhaHash = usuario.getSenhaHash();
        this.criadoEm = usuario.getCriadoEm();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    /** E o "Membro desde" da tela de perfil. */
    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /** Devolve o hash bcrypt. Quem compara e o PasswordEncoder, nunca um equals de String. */
    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
