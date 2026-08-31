package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Itens copiados para o diario. Normalmente sao salvos junto com o registro, pelo
 * RegistroDiarioRepository; aqui ficam as consultas do dashboard (RF006).
 */
public interface ItemRegistroRepository extends JpaRepository<ItemRegistro, Long> {

    Optional<ItemRegistro> findByIdAndRegistroDiarioUsuarioId(Long id, Long usuarioId);

    @EntityGraph(attributePaths = "itemMercado")
    List<ItemRegistro> findByRegistroDiarioIdAndRegistroDiarioUsuarioId(
            Long registroDiarioId, Long usuarioId);

        /**
         * Base do cartao de item de maior impacto. Agrupar e descartar as linhas sem preco fica no
         * Service, que e onde a conversao de unidade acontece.
         */
    @EntityGraph(attributePaths = "itemMercado")
    List<ItemRegistro> findByRegistroDiarioUsuarioIdAndRegistroDiarioDataBetween(
            Long usuarioId, LocalDate inicio, LocalDate fim);

    /** Usado antes de desativar um item de mercado, para saber se o diario ja consumiu ele. */
    boolean existsByItemMercadoIdAndItemMercadoUsuarioId(Long itemMercadoId, Long usuarioId);

        /**
         * Sem filtro de usuario de proposito: e carga unica de manutencao da base inteira (ver
         * CongelarPrecoDoDiario), nao consulta que responde a alguem.
         */
    @EntityGraph(attributePaths = "itemMercado")
    List<ItemRegistro> findByPrecoNoConsumoIsNull();
}
