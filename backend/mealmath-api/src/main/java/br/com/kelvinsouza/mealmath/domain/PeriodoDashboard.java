package br.com.kelvinsouza.mealmath.domain;

/**
 * Janela que o dashboard consolida (RF006).
 *
 * DIA e MES sao janelas moveis terminando hoje: MES sao os ultimos 30 dias e nao "o mes de
 * agosto". Pelo calendario, fevereiro pareceria sempre mais barato que janeiro so por ser mais
 * curto, e a comparacao com o periodo anterior perderia o sentido.
 *
 * SEMANA e a excecao: e a semana do calendario, de domingo a sabado, a mesma que o Diario recorta.
 * Uma janela movel aqui faria "a semana" significar duas coisas conforme a tela.
 *
 * Nao confundir com PeriodoMeta, que e a base do limite de gasto (RF010): da para ter meta mensal
 * e olhar o dashboard da semana.
 */
public enum PeriodoDashboard {

    DIA(1),
    SEMANA(7),
    MES(30);

    private final int dias;

    PeriodoDashboard(int dias) {
        this.dias = dias;
    }

    /** Dias da janela. E o "M" do indicador "N de M dias" que aparece na tela. */
    public int getDias() {
        return dias;
    }
}
