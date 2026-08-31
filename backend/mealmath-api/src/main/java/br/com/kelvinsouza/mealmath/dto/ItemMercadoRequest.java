package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.Categoria;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada do cadastro e da atualizacao de item de mercado (RF004/RF007).
 *
 * A categoria e opcional: vindo nula cai em OUTROS em vez de dar 400, porque classificar o item
 * so ajuda a ler o dashboard e nao entra no calculo. Obrigatoria, atrapalharia o cadastro rapido.
 */
public record ItemMercadoRequest(
        @NotBlank(message = "Informe o nome do item.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,
        @NotNull(message = "Informe o preço pago.")
        @Positive(message = "O preço deve ser maior que zero.")
        BigDecimal preco,
        @NotNull(message = "Informe a quantidade da embalagem.")
        @Positive(message = "A quantidade da embalagem deve ser maior que zero.")
        BigDecimal quantidadeEmbalagem,
        @NotNull(message = "Informe a unidade de medida (KG, G, L, ML ou UN).")
        UnidadeMedida unidade,
        Categoria categoria) {

    /** Devolve a categoria escolhida, ou OUTROS quando o campo nao vem preenchido. */
    public Categoria categoriaOuPadrao() {
        return categoria == null ? Categoria.OUTROS : categoria;
    }
}
