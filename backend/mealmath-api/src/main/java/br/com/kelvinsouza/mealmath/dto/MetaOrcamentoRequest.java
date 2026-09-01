package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.PeriodoMeta;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Dados de entrada para definir a meta de orcamento (RF009). Serve para criar e para atualizar,
 * porque a meta e unica por usuario e o PUT sobrescreve tudo.
 *
 * O @Positive pega o caso de valor zero ou negativo antes de salvar, e nao na hora de calcular o
 * progresso. O @Digits impede que 250,999 seja arredondado sozinho para 251,00 pela coluna
 * numeric(12,2), mudando o limite de gasto que o usuario informou.
 */
public record MetaOrcamentoRequest(
        @NotNull(message = "Informe o valor-limite da meta.")
        @Positive(message = "O valor da meta deve ser maior que zero.")
        @Digits(
                integer = 10,
                fraction = 2,
                message = "O valor da meta deve ter no máximo 2 casas decimais.")
        BigDecimal valor,
        @NotNull(message = "Informe o período da meta (SEMANAL ou MENSAL).")
        PeriodoMeta periodo) {}
