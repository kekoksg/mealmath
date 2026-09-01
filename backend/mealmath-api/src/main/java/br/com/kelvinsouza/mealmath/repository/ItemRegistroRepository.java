package br.com.kelvinsouza.mealmath.repository;

import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Diz se a linha existe, mas dentro do diario de outra conta. Serve so para a AuditoriaDeAcesso
     * registrar em WARN a tentativa de ler dado alheio, sem mudar a resposta, que continua 404.
     *
     * Aqui o dono e alcancado pela relacao (registroDiario.usuario.id). E chamada apenas depois de o
     * item nao ter sido achado dentro do registro ja carregado, entao nao encosta no caminho das
     * requisicoes que dao certo. Item de outro registro do PROPRIO usuario nao entra: e erro de
     * navegacao, e nao acesso a dado de terceiro.
     */
    @Query(
            """
            select count(i) > 0
            from ItemRegistro i
            where i.id = :id
              and i.registroDiario.usuario.id <> :usuarioId
            """)
    boolean existeDeOutroUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

        /**
         * Sem filtro de usuario de proposito: e carga unica de manutencao da base inteira (ver
         * CongelarPrecoDoDiario), nao consulta que responde a alguem.
         */
    @EntityGraph(attributePaths = "itemMercado")
    List<ItemRegistro> findByPrecoNoConsumoIsNull();
}
