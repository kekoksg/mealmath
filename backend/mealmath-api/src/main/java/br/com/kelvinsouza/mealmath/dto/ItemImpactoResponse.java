package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.Categoria;
import java.math.BigDecimal;

/**
 * Item que mais pesou no orcamento do periodo (RF006).
 *
 * O que conta e o custo acumulado no periodo e nao o preco da embalagem. Um item barato consumido
 * todo dia pode passar um caro consumido uma vez so, e mostrar isso e a ideia do dashboard.
 */
public record ItemImpactoResponse(
        Long itemMercadoId,
        String nome,
        Categoria categoria,
        BigDecimal custo,
        BigDecimal percentual) {}
