package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Diz se o id existe, mas em outra conta. Serve so para a AuditoriaDeAcesso registrar em WARN a
     * tentativa de ler dado alheio, sem mudar a resposta, que continua 404 nos dois casos.
     *
     * E chamada apenas depois de o findByIdAndUsuarioId ja ter voltado vazio, entao nao encosta no
     * caminho das requisicoes que dao certo.
     */
    @Query(
            """
            select count(i) > 0
            from ItemMercado i
            where i.id = :id
              and i.usuario.id <> :usuarioId
            """)
    boolean existeDeOutroUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);
}
