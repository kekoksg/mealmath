package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.Refeicao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * Linhas que ainda estao com o emoji antigo, de antes da troca para os icones do Solar.
     * Nao tem filtro de usuario de proposito: e uma carga unica de manutencao da base inteira
     * (ver MigrarIconesParaSolar), nao uma consulta que responde a alguem.
     */
    List<Refeicao> findByIconeNotLike(String padrao);
}
