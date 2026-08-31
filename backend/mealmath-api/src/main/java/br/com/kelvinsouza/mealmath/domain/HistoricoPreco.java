package br.com.kelvinsouza.mealmath.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Guarda o preco antigo de um item de mercado (RF007).
 *
 * Quando o usuario atualiza o preco, o valor anterior nao e sobrescrito e sim empilhado aqui.
 * A embalagem e a unidade tambem sao salvas porque o custo unitario depende das tres coisas.
 * Comparando so o preco, um item que passou de R$ 5,20 o litro para R$ 5,60 os 2 litros
 * apareceria como alta, quando na verdade ficou mais barato.
 */
@Entity
@Table(
        name = "historico_preco",
        indexes = @Index(name = "ix_historico_preco_item_data", columnList = "item_mercado_id, substituido_em"))
public class HistoricoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_mercado_id", nullable = false)
    private ItemMercado itemMercado;

    /** Preco que valia antes da alteracao. */
    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @NotNull
    @Positive
    @Column(name = "quantidade_embalagem", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeEmbalagem;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private UnidadeMedida unidade;

    @CreationTimestamp
    @Column(name = "substituido_em", nullable = false, updatable = false)
    private Instant substituidoEm;

    protected HistoricoPreco() {
        // exigido pelo JPA
    }

    public HistoricoPreco(
            ItemMercado itemMercado,
            BigDecimal preco,
            BigDecimal quantidadeEmbalagem,
            UnidadeMedida unidade) {
        this.itemMercado = itemMercado;
        this.preco = preco;
        this.quantidadeEmbalagem = quantidadeEmbalagem;
        this.unidade = unidade;
    }

    public Long getId() {
        return id;
    }

    public ItemMercado getItemMercado() {
        return itemMercado;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public BigDecimal getQuantidadeEmbalagem() {
        return quantidadeEmbalagem;
    }

    public UnidadeMedida getUnidade() {
        return unidade;
    }

    public Instant getSubstituidoEm() {
        return substituidoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HistoricoPreco outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
