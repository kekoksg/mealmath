package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.Categoria;
import java.math.BigDecimal;

/**
 * Uma fatia do grafico de onde vai o dinheiro (RF006).
 *
 * So aparecem categorias que tiveram gasto no periodo, para nao ficar fatia invisivel na legenda.
 * A lista ja vem ordenada do maior custo para o menor.
 */
public record ComposicaoCategoriaResponse(
        Categoria categoria, String rotulo, BigDecimal custo, BigDecimal percentual) {}
