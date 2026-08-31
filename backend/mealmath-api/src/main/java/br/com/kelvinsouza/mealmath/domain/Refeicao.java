package br.com.kelvinsouza.mealmath.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Refeicao modelo da biblioteca (RF003).
 *
 * Nao representa consumo. O consumo de um dia e o RegistroDiario, que tem copia propria dos itens.
 * Editar o almoco de hoje no diario nunca altera esse modelo aqui.
 */
@Entity
@Table(name = "refeicao", indexes = @Index(name = "ix_refeicao_usuario", columnList = "usuario_id"))
public class Refeicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String titulo;

    /** Nome do icone escolhido na tela (ex.: "cafe-da-manha"). */
    @Column(length = 60)
    private String icone;

    @OneToMany(
            mappedBy = "refeicao",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    // Sem o OrderBy a ordem e a que o banco resolver devolver, e ela muda quando as linhas sao
    // reescritas. Os itens ficavam trocando de posicao entre uma requisicao e outra.
    // Ordenando por id fica na ordem em que o usuario adicionou os itens.
    @OrderBy("id ASC")
    private List<ItemRefeicao> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Refeicao() {
        // exigido pelo JPA
    }

    public Refeicao(Usuario usuario, String titulo, String icone) {
        this.usuario = usuario;
        this.titulo = titulo;
        this.icone = icone;
    }

    public void adicionarItem(ItemRefeicao item) {
        itens.add(item);
        item.setRefeicao(this);
    }

    public void removerItem(ItemRefeicao item) {
        itens.remove(item);
        item.setRefeicao(null);
    }

    /**
     * Esvazia a lista de itens para a refeicao ser remontada na edicao.
     *
     * Altero a lista que ja existe em vez de criar uma nova, porque o orphanRemoval so apaga as
     * linhas orfas se o Hibernate continuar enxergando a mesma colecao que ele carregou.
     */
    public void limparItens() {
        itens.forEach(item -> item.setRefeicao(null));
        itens.clear();
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getIcone() {
        return icone;
    }

    public void setIcone(String icone) {
        this.icone = icone;
    }

    /** Lista so de leitura. Use adicionarItem e removerItem para manter os dois lados do vinculo. */
    public List<ItemRefeicao> getItens() {
        return Collections.unmodifiableList(itens);
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
        if (!(o instanceof Refeicao outra)) {
            return false;
        }
        return id != null && id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
