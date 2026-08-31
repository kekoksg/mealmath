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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Produto comprado no mercado, na unidade da embalagem (RF004).
 *
 * A exclusao e logica (ativo = false) porque os registros do diario apontam para esse item.
 * Apagar a linha de vez deixaria o diario orfao.
 */
@Entity
@Table(
        name = "item_mercado",
        indexes = {
            @Index(name = "ix_item_mercado_usuario", columnList = "usuario_id"),
            @Index(name = "ix_item_mercado_usuario_nome", columnList = "usuario_id, nome")
        })
public class ItemMercado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String nome;

    /** Preco pago pela embalagem inteira. Valor em dinheiro e sempre BigDecimal. */
    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    /** Quantidade que vem na embalagem, na unidade de compra. Tem que ser maior que zero. */
    @NotNull
    @Positive
    @Column(name = "quantidade_embalagem", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeEmbalagem;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private UnidadeMedida unidade;

    /**
     * Categoria usada no grafico de composicao do dashboard (RF006). Nao entra em calculo nenhum.
     * Nunca fica nula: item sem categoria sumiria do grafico e as fatias nao fechariam com o total.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria = Categoria.OUTROS;

    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ItemMercado() {
        // exigido pelo JPA
    }

    public ItemMercado(
            Usuario usuario,
            String nome,
            BigDecimal preco,
            BigDecimal quantidadeEmbalagem,
            UnidadeMedida unidade) {
        this(usuario, nome, preco, quantidadeEmbalagem, unidade, Categoria.OUTROS);
    }

    public ItemMercado(
            Usuario usuario,
            String nome,
            BigDecimal preco,
            BigDecimal quantidadeEmbalagem,
            UnidadeMedida unidade,
            Categoria categoria) {
        this.usuario = usuario;
        this.nome = nome;
        this.preco = preco;
        this.quantidadeEmbalagem = quantidadeEmbalagem;
        this.unidade = unidade;
        setCategoria(categoria);
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public BigDecimal getQuantidadeEmbalagem() {
        return quantidadeEmbalagem;
    }

    public void setQuantidadeEmbalagem(BigDecimal quantidadeEmbalagem) {
        this.quantidadeEmbalagem = quantidadeEmbalagem;
    }

    public UnidadeMedida getUnidade() {
        return unidade;
    }

    public void setUnidade(UnidadeMedida unidade) {
        this.unidade = unidade;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria == null ? Categoria.OUTROS : categoria;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
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
        if (!(o instanceof ItemMercado outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
