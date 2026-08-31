package br.com.kelvinsouza.mealmath.service;

import java.math.BigDecimal;

/** Custo de um item da refeicao, ainda sem arredondar. Quem arredonda para 2 casas e o front. */
public record CustoItem(String descricao, BigDecimal custo) {}
