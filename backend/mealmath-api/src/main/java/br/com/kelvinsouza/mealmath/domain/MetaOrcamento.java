package br.com.kelvinsouza.mealmath.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Limite de gasto com alimentacao em um periodo (RF010).
 *
 * Cada usuario tem no maximo uma meta, garantido pela chave unica. O periodo e um campo dela e nao
 * parte da identidade, entao trocar de MENSAL para SEMANAL atualiza a meta que ja existe em vez de
 * criar outra.
 */
@Entity
@Table(
        name = "meta_orcamento",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_meta_orcamento_usuario",
                        columnNames = "usuario_id"))
public class MetaOrcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeriodoMeta periodo;

    /** Valor limite do gasto. Tem que ser maior que zero (RF010). */
    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected MetaOrcamento() {
        // exigido pelo JPA
    }

    public MetaOrcamento(Usuario usuario, PeriodoMeta periodo, BigDecimal valor) {
        this.usuario = usuario;
        this.periodo = periodo;
        this.valor = valor;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public PeriodoMeta getPeriodo() {
        return periodo;
    }

    public void setPeriodo(PeriodoMeta periodo) {
        this.periodo = periodo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MetaOrcamento outra)) {
            return false;
        }
        return id != null && id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
