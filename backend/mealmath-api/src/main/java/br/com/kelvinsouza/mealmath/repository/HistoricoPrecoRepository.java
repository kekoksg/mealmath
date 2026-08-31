package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.HistoricoPreco;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Historico de precos (RF007). Aqui o filtro por usuario e feito navegando pela relacao
 * (itemMercado.usuario.id), para nao precisar repetir a coluna de usuario nessa tabela.
 */
public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, Long> {

    List<HistoricoPreco> findByItemMercadoIdAndItemMercadoUsuarioIdOrderBySubstituidoEmDesc(
            Long itemMercadoId, Long usuarioId);

    /** Preco anterior do item. E com ele que o dashboard calcula a variacao (RF006). */
    Optional<HistoricoPreco> findFirstByItemMercadoIdAndItemMercadoUsuarioIdOrderBySubstituidoEmDesc(
            Long itemMercadoId, Long usuarioId);

    /** Trocas de preco do usuario a partir de uma data, para o alerta de alta recente. */
    @EntityGraph(attributePaths = "itemMercado")
    List<HistoricoPreco> findByItemMercadoUsuarioIdAndSubstituidoEmAfterOrderBySubstituidoEmDesc(
            Long usuarioId, Instant desde, Limit limite);

        /**
         * Ultima troca de preco de cada item ativo do usuario, base do alerta de alta (RF006).
         *
         * Cada linha e o penultimo preco do item, porque o ultimo esta no proprio ItemMercado. Item que
         * nunca mudou de preco nao aparece: sem dois valores nao existe variacao.
         *
         * O desempate e pelo id e nao pelo substituidoEm porque duas atualizacoes do mesmo item na mesma
         * transacao ficam com o mesmo timestamp, e voltariam duas linhas, duplicando o alerta.
         */
    @EntityGraph(attributePaths = "itemMercado")
    @Query(
            """
            select h
            from HistoricoPreco h
            where h.itemMercado.usuario.id = :usuarioId
              and h.itemMercado.ativo = true
              and h.id = (
                    select max(anterior.id)
                    from HistoricoPreco anterior
                    where anterior.itemMercado = h.itemMercado
              )
            """)
    List<HistoricoPreco> buscarUltimaTrocaDeCadaItem(@Param("usuarioId") Long usuarioId);
}
