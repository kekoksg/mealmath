package br.com.kelvinsouza.mealmath.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Refeicao que foi realmente consumida em um dia (RF008). E daqui, e nao da biblioteca, que sai o
 * custo do periodo.
 *
 * O refeicaoOrigemId serve so de rastreio e fica nulo se o modelo foi apagado da biblioteca, sem
 * que isso mude nada no registro.
 */
public record RegistroDiarioResponse(
        Long id,
        LocalDate data,
        String titulo,
        String icone,
        Long refeicaoOrigemId,
        List<ItemRegistroResponse> itens,
        BigDecimal custoTotal,
        List<String> itensSemPreco) {

    public boolean possuiItensSemPreco() {
        return !itensSemPreco.isEmpty();
    }
}
