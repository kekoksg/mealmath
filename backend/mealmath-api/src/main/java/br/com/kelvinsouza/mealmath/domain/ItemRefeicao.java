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
import java.util.Objects;

/**
 * Item de uma refeicao modelo da biblioteca: o item de mercado mais a quantidade padrao consumida
 * dele (RF003).
 *
 * A quantidade pode estar numa unidade diferente da embalagem, desde que seja da mesma grandeza.
 * Comprar em KG e consumir em G e o caso normal. Quem valida essa compatibilidade e o Service.
 */
@Entity
@Table(
        name = "item_refeicao",
        indexes = {
            @Index(name = "ix_item_refeicao_refeicao", columnList = "refeicao_id"),
            @Index(name = "ix_item_refeicao_item_mercado", columnList = "item_mercado_id")
        })
public class ItemRefeicao implements ItemConsumido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refeicao_id", nullable = false)
    private Refeicao refeicao;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_mercado_id", nullable = false)
    private ItemMercado itemMercado;

    /** Quantidade consumida, na unidade de consumo. Tem que ser maior que zero. */
    @NotNull
    @Positive
    @Column(name = "quantidade_consumida", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeConsumida;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private UnidadeMedida unidade;

    protected ItemRefeicao() {
        // exigido pelo JPA
    }

    public ItemRefeicao(ItemMercado itemMercado, BigDecimal quantidadeConsumida, UnidadeMedida unidade) {
        this.itemMercado = itemMercado;
        this.quantidadeConsumida = quantidadeConsumida;
        this.unidade = unidade;
    }

    public Long getId() {
        return id;
    }

    public Refeicao getRefeicao() {
        return refeicao;
    }

    void setRefeicao(Refeicao refeicao) {
        this.refeicao = refeicao;
    }

    @Override
    public ItemMercado getItemMercado() {
        return itemMercado;
    }

    /**
     * Aqui vale sempre o preco atual. A biblioteca e um modelo para consumo futuro, entao o custo
     * dela tem que responder "quanto isso sairia hoje".
     */
    @Override
    public BasePreco getBasePreco() {
        return BasePreco.vigenteDe(itemMercado);
    }

    /** Na biblioteca o vinculo e obrigatorio, entao o nome vem direto do item de mercado. */
    @Override
    public String getDescricao() {
        return itemMercado.getNome();
    }

    public void setItemMercado(ItemMercado itemMercado) {
        this.itemMercado = itemMercado;
    }

    @Override
    public BigDecimal getQuantidadeConsumida() {
        return quantidadeConsumida;
    }

    public void setQuantidadeConsumida(BigDecimal quantidadeConsumida) {
        this.quantidadeConsumida = quantidadeConsumida;
    }

    @Override
    public UnidadeMedida getUnidade() {
        return unidade;
    }

    public void setUnidade(UnidadeMedida unidade) {
        this.unidade = unidade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemRefeicao outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
