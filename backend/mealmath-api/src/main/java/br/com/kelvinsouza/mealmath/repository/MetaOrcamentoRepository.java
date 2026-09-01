package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.MetaOrcamento;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Meta de orcamento (RF009). Como e uma meta por usuario, buscar pelo dono ja acha o registro
 * certo. Nao tem consulta por id aqui, entao nao existe caminho que chegue na meta de outra pessoa.
 *
 * Optional vazio e resultado esperado e nao erro: sem meta definida o dashboard esconde o progresso
 * e oferece o botao para definir.
 */
public interface MetaOrcamentoRepository extends JpaRepository<MetaOrcamento, Long> {

    Optional<MetaOrcamento> findByUsuarioId(Long usuarioId);
}
