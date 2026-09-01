package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.dto.MetaOrcamentoResponse;

/**
 * Resultado de definir a meta de orcamento (RF009).
 *
 * O campo criada e true quando o usuario ainda nao tinha meta. Serve para o controller responder
 * 201 na primeira vez e 200 na atualizacao, e para a tela escolher entre "Meta definida" e
 * "Meta atualizada".
 */
public record ResultadoDefinicaoMeta(MetaOrcamentoResponse meta, boolean criada) {}
