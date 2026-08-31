package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Diario de consumo (RF009). E a unica fonte do custo consolidado do dashboard (RF006). */
public interface RegistroDiarioRepository extends JpaRepository<RegistroDiario, Long> {

    @EntityGraph(attributePaths = {"itens", "itens.itemMercado"})
    Optional<RegistroDiario> findByIdAndUsuarioId(Long id, Long usuarioId);

    @EntityGraph(attributePaths = {"itens", "itens.itemMercado"})
    List<RegistroDiario> findByUsuarioIdAndDataOrderByIdAsc(Long usuarioId, LocalDate data);

    /** Consumo de um periodo. Serve tanto para o periodo atual quanto para o anterior (RF006). */
    @EntityGraph(attributePaths = {"itens", "itens.itemMercado"})
    List<RegistroDiario> findByUsuarioIdAndDataBetweenOrderByDataAscIdAsc(
            Long usuarioId, LocalDate inicio, LocalDate fim);

    /**
     * Quantos dias diferentes tem registro no periodo. E o "N" do indicador "N de M dias".
     * Dia sem registro nao e um dia de R$ 0,00, e essa falta precisa aparecer na tela.
     */
    @Query(
            """
            select count(distinct r.data)
            from RegistroDiario r
            where r.usuario.id = :usuarioId
              and r.data between :inicio and :fim
            """)
    long contarDiasComRegistro(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    boolean existsByUsuarioIdAndData(Long usuarioId, LocalDate data);

        /**
         * Tira o vinculo de rastreio antes de apagar uma refeicao da biblioteca. Sem isso a chave
         * estrangeira impediria a exclusao, e apagar o registro junto misturaria biblioteca com diario.
         *
         * O clearAutomatically e obrigatorio: o UPDATE vai direto no banco e nao atualiza os
         * RegistroDiario ja carregados, que continuariam apontando para a refeicao apagada e quebrariam
         * o flush. Como o contexto e limpo, recarregue o que precisar depois de chamar.
         */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update RegistroDiario r
            set r.refeicaoOrigem = null
            where r.refeicaoOrigem.id = :refeicaoId
              and r.usuario.id = :usuarioId
            """)
    int desvincularRefeicaoOrigem(
            @Param("refeicaoId") Long refeicaoId, @Param("usuarioId") Long usuarioId);

    /**
     * Linhas que ainda estao com o emoji antigo, de antes da troca para os icones do Solar.
     * Nao tem filtro de usuario de proposito: e uma carga unica de manutencao da base inteira
     * (ver MigrarIconesParaSolar), nao uma consulta que responde a alguem.
     */
    List<RegistroDiario> findByIconeNotLike(String padrao);
}
