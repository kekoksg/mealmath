package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Itens de mercado (RF004/RF007). Todo metodo filtra pelo usuario autenticado. Nao existe
 * findById solto aqui, de proposito: use o findByIdAndUsuarioId.
 */
public interface ItemMercadoRepository extends JpaRepository<ItemMercado, Long> {

    Optional<ItemMercado> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<ItemMercado> findByUsuarioIdAndAtivoTrueOrderByNomeAsc(Long usuarioId);

    /** Usado no fluxo do RF004 de sugerir atualizar o preco quando o item ja existe. */
    Optional<ItemMercado> findByUsuarioIdAndNomeIgnoreCaseAndAtivoTrue(Long usuarioId, String nome);

    boolean existsByUsuarioIdAndNomeIgnoreCaseAndAtivoTrue(Long usuarioId, String nome);
}
