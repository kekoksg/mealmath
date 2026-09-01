package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.PeriodoMeta;
import java.math.BigDecimal;

/**
 * Progresso do gasto em cima da meta de orcamento (RF006 e RF009).
 *
 * O valorNoPeriodo e a meta rateada para o tamanho da janela: uma meta mensal de R$ 450,00 vista
 * em 7 dias vale R$ 105,00. Comparar o gasto da semana com a meta do mes daria sempre "dentro da
 * meta".
 *
 * O percentualConsumido nao para em 100 de proposito. Estourar a meta em 130% e informacao util;
 * quem decide onde parar de preencher e a barra de progresso na tela.
 */
public record ProgressoMetaResponse(
        BigDecimal valor,
        PeriodoMeta periodo,
        BigDecimal valorNoPeriodo,
        BigDecimal percentualConsumido,
        BigDecimal saldo,
        boolean acimaDaMeta) {}
