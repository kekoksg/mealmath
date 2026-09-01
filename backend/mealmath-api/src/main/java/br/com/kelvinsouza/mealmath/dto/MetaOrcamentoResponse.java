package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.MetaOrcamento;
import br.com.kelvinsouza.mealmath.domain.PeriodoMeta;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Meta de orcamento atual do usuario (RF009).
 *
 * Quando nao existe meta, o endpoint devolve 204 sem corpo em vez de um record zerado. Assim o
 * dashboard consegue diferenciar "meta nao definida", que esconde a barra e oferece o botao de
 * definir, de uma meta de valor zero, que nem pode existir porque o valor e sempre maior que zero.
 */
public record MetaOrcamentoResponse(
        Long id, BigDecimal valor, PeriodoMeta periodo, Instant atualizadoEm) {

    public static MetaOrcamentoResponse de(MetaOrcamento meta) {
        return new MetaOrcamentoResponse(
                meta.getId(), meta.getValor(), meta.getPeriodo(), meta.getAtualizadoEm());
    }
}
