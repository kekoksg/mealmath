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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Refeicao que o usuario realmente consumiu numa data (RF008). E daqui que sai o custo do
 * dashboard (RF006); a biblioteca nunca entra na soma.
 *
 * Titulo, icone e itens sao copiados na criacao, nao sao referencias ao modelo. O refeicaoOrigem
 * so rastreia a origem e pode ser nulo: apagar uma refeicao da biblioteca nao mexe no historico.
 *
 * A data e LocalDate, sem hora e sem fuso, para a refeicao nao mudar de dia conforme o navegador.
 */
@Entity
@Table(
        name = "registro_diario",
        indexes = @Index(name = "ix_registro_diario_usuario_data", columnList = "usuario_id, data"))
public class RegistroDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Column(nullable = false)
    private LocalDate data;

    /** Modelo que deu origem ao registro. So de rastreio, pode ser nulo e nao entra no calculo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refeicao_origem_id")
    private Refeicao refeicaoOrigem;

    /** Copia do titulo no momento do consumo, para renomear o modelo nao mexer no passado. */
    @NotBlank
    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 60)
    private String icone;

    @OneToMany(
            mappedBy = "registroDiario",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    // Sem o OrderBy a ordem e a que o banco resolver devolver, e ela muda quando as linhas sao
    // reescritas. Os itens ficavam trocando de posicao entre uma requisicao e outra.
    // Ordenando por id fica na ordem em que o usuario adicionou os itens.
    @OrderBy("id ASC")
    private List<ItemRegistro> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected RegistroDiario() {
        // exigido pelo JPA
    }

    public RegistroDiario(Usuario usuario, LocalDate data, String titulo, String icone, Refeicao refeicaoOrigem) {
        this.usuario = usuario;
        this.data = data;
        this.titulo = titulo;
        this.icone = icone;
        this.refeicaoOrigem = refeicaoOrigem;
    }

    public void adicionarItem(ItemRegistro item) {
        itens.add(item);
        item.setRegistroDiario(this);
    }

    public void removerItem(ItemRegistro item) {
        itens.remove(item);
        item.setRegistroDiario(null);
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Refeicao getRefeicaoOrigem() {
        return refeicaoOrigem;
    }

    public void setRefeicaoOrigem(Refeicao refeicaoOrigem) {
        this.refeicaoOrigem = refeicaoOrigem;
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
    public List<ItemRegistro> getItens() {
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
        if (!(o instanceof RegistroDiario outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
