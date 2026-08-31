package br.com.kelvinsouza.mealmath.security;

import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca o usuario pelo e-mail para o DaoAuthenticationProvider.
 *
 * Criar esse bean tambem desliga aquele usuario "user" com senha gerada que o Spring Boot cria
 * sozinho quando nao acha nenhum UserDetailsService. E uma conta que funciona de verdade e nao
 * deveria existir nem em desenvolvimento.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return usuarioRepository
                .findByEmailIgnoreCase(email)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }
}
