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
import java.util.Objects;

/**
 * Item consumido dentro de um RegistroDiario: copia propria, com id proprio, e nao uma referencia
 * ao ItemRefeicao da biblioteca. E isso que garante que mudar a quantidade do almoco de hoje nao
 * altere o modelo nem os outros dias.
 *
 * O preco tambem e copiado (ver getBasePreco). O que ja foi comido custou o que custou naquele dia,
 * entao atualizar o preco no mercado (RF007) so vale do consumo em diante.
 */
@Entity
@Table(
        name = "item_registro",
        indexes = {
            @Index(name = "ix_item_registro_registro", columnList = "registro_diario_id"),
            @Index(name = "ix_item_registro_item_mercado", columnList = "item_mercado_id")
        })
public class ItemRegistro implements ItemConsumido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_diario_id", nullable = false)
    private RegistroDiario registroDiario;

    /**
     * Item de mercado de origem. Pode ser nulo. Quando for, o Service tira a linha do total e
     * avisa o usuario, em vez de contar como R$ 0,00.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_mercado_id")
    private ItemMercado itemMercado;

    /** Copia do nome no momento do consumo, para o registro continuar legivel mesmo sem vinculo. */
    @NotBlank
    @Column(name = "nome_item", nullable = false, length = 120)
    private String nomeItem;

    /** Quantidade consumida nesse dia, na unidade de consumo. Tem que ser maior que zero. */
    @NotNull
    @Positive
    @Column(name = "quantidade_consumida", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeConsumida;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private UnidadeMedida unidade;

    /**
     * Preco da embalagem no dia do consumo. Essas tres colunas juntas formam a base congelada.
     * Sao as tres, e nao so o preco, porque o custo unitario depende de todas elas.
     *
     * Aceitam nulo por causa das linhas que ja estavam gravadas antes dessa regra existir. Elas
     * nasceram sem a copia do preco e sao preenchidas uma unica vez na subida da aplicacao, pela
     * classe CongelarPrecoDoDiario. Enquanto estiverem nulas o getBasePreco usa o preco atual.
     */
    @Column(name = "preco_no_consumo", precision = 12, scale = 2)
    private BigDecimal precoNoConsumo;

    @Column(name = "quantidade_embalagem_no_consumo", precision = 12, scale = 4)
    private BigDecimal quantidadeEmbalagemNoConsumo;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_embalagem_no_consumo", length = 3)
    private UnidadeMedida unidadeEmbalagemNoConsumo;

    protected ItemRegistro() {
        // exigido pelo JPA
    }

    /**
     * Copio o preco aqui no construtor, e nao em quem chama, para toda linha nova ja nascer com o
     * preco do momento, venha ela da biblioteca, da repeticao do dia anterior ou da carga de
     * desenvolvimento. Assim nao tem como esquecer de congelar.
     */
    public ItemRegistro(
            ItemMercado itemMercado,
            String nomeItem,
            BigDecimal quantidadeConsumida,
            UnidadeMedida unidade) {
        this.itemMercado = itemMercado;
        this.nomeItem = nomeItem;
        this.quantidadeConsumida = quantidadeConsumida;
        this.unidade = unidade;
        congelarPreco(BasePreco.vigenteDe(itemMercado));
    }

    public void congelarPreco(BasePreco base) {
        if (base == null) {
            return;
        }
        this.precoNoConsumo = base.preco();
        this.quantidadeEmbalagemNoConsumo = base.quantidadeEmbalagem();
        this.unidadeEmbalagemNoConsumo = base.unidade();
    }

    /** So da true em linha gravada antes da regra de congelar o preco existir. */
    public boolean semPrecoCongelado() {
        return precoNoConsumo == null
                || quantidadeEmbalagemNoConsumo == null
                || unidadeEmbalagemNoConsumo == null;
    }

    /**
     * Devolve o preco congelado no consumo. So cai no preco atual nas linhas antigas, que ainda
     * nao tem a copia, e nesse caso o valor volta a variar como era antes.
     */
    @Override
    public BasePreco getBasePreco() {
        if (semPrecoCongelado()) {
            return BasePreco.vigenteDe(itemMercado);
        }
        return new BasePreco(precoNoConsumo, quantidadeEmbalagemNoConsumo, unidadeEmbalagemNoConsumo);
    }

    public Long getId() {
        return id;
    }

    public RegistroDiario getRegistroDiario() {
        return registroDiario;
    }

    void setRegistroDiario(RegistroDiario registroDiario) {
        this.registroDiario = registroDiario;
    }

    @Override
    public ItemMercado getItemMercado() {
        return itemMercado;
    }

    /** Vem da copia local e nao do item de mercado, para o registro continuar legivel sem vinculo. */
    @Override
    public String getDescricao() {
        return nomeItem;
    }

    public void setItemMercado(ItemMercado itemMercado) {
        this.itemMercado = itemMercado;
    }

    public String getNomeItem() {
        return nomeItem;
    }

    public void setNomeItem(String nomeItem) {
        this.nomeItem = nomeItem;
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
        if (!(o instanceof ItemRegistro outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
