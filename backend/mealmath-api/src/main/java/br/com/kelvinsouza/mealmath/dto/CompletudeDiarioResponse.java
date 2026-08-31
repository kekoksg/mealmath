package br.com.kelvinsouza.mealmath.dto;

/**
 * O indicador "N de M dias" da tela (RF006).
 *
 * Serve para mostrar o que a media esconde. O custo medio por dia divide pelos M dias do periodo,
 * mas dia sem registro nao e dia de R$ 0,00, e sim dia sem informacao. Quem registrou 2 de 30 dias
 * ve uma media baixissima, e esse par de numeros e o que explica o motivo.
 */
public record CompletudeDiarioResponse(long diasComRegistro, int totalDeDias) {}
