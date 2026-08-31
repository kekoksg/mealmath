package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Unico repositorio sem filtro por usuario, porque e ele que representa o usuario (RF001/RF002). */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
