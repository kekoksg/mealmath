package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.Refeicao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Biblioteca de refeicoes modelo (RF003). Nunca entra na soma do dashboard, para o custo
 * consolidado quem serve e o RegistroDiarioRepository.
 *
 * As buscas trazem os itens e os itens de mercado em um SELECT so, para o calculo do custo nao
 * cair no problema do N+1.
 */
public interface RefeicaoRepository extends JpaRepository<Refeicao, Long> {

    @EntityGraph(attributePaths = {"itens", "itens.itemMercado"})
    List<Refeicao> findByUsuarioIdOrderByTituloAsc(Long usuarioId);

    @EntityGraph(attributePaths = {"itens", "itens.itemMercado"})
    Optional<Refeicao> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    /**
     * Diz se o id existe, mas em outra conta. Serve so para a AuditoriaDeAcesso registrar em WARN a
     * tentativa de ler dado alheio, sem mudar a resposta, que continua 404 nos dois casos.
     *
     * E chamada apenas depois de a busca escopada por usuario ja ter voltado vazia, entao nao
     * encosta no caminho das requisicoes que dao certo.
     */
    @Query(
            """
            select count(r) > 0
            from Refeicao r
            where r.id = :id
              and r.usuario.id <> :usuarioId
            """)
    boolean existeDeOutroUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Linhas que ainda estao com o emoji antigo, de antes da troca para os icones do Solar.
     * Nao tem filtro de usuario de proposito: e uma carga unica de manutencao da base inteira
     * (ver MigrarIconesParaSolar), nao uma consulta que responde a alguem.
     */
    List<Refeicao> findByIconeNotLike(String padrao);
}
