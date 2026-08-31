package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Itens das refeicoes modelo. Normalmente eles sao salvos junto com a refeicao, pelo
 * RefeicaoRepository, por causa do cascade. Esse repositorio serve para consultas pontuais, sempre
 * filtrando pelo dono da refeicao.
 */
public interface ItemRefeicaoRepository extends JpaRepository<ItemRefeicao, Long> {

    @EntityGraph(attributePaths = "itemMercado")
    List<ItemRefeicao> findByRefeicaoIdAndRefeicaoUsuarioId(Long refeicaoId, Long usuarioId);

    Optional<ItemRefeicao> findByIdAndRefeicaoUsuarioId(Long id, Long usuarioId);

    /** Usado antes de desativar um item de mercado, para saber se alguma refeicao usa ele. */
    boolean existsByItemMercadoIdAndItemMercadoUsuarioId(Long itemMercadoId, Long usuarioId);

    @EntityGraph(attributePaths = "refeicao")
    List<ItemRefeicao> findByItemMercadoIdAndItemMercadoUsuarioId(Long itemMercadoId, Long usuarioId);
}
