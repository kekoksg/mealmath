package br.com.kelvinsouza.mealmath.domain;

/** Periodo de referencia da meta de orcamento (RF009). */
public enum PeriodoMeta {

    SEMANAL(7),
    MENSAL(30);

    private final int dias;

    PeriodoMeta(int dias) {
        this.dias = dias;
    }

    /**
     * Dias que a meta cobre. O dashboard (RF006) usa isso para ratear a meta quando o periodo dela
     * e diferente da janela consultada: uma meta mensal olhada numa janela de 7 dias vale
     * valor / 30 * 7, e nao o valor cheio.
     *
     * O mes esta fixo em 30 dias, o mesmo numero do PeriodoDashboard.MES. Se as duas contagens
     * fossem diferentes, um mes sem gasto nenhum a mais ia aparecer como 96% ou 103% da meta
     * dependendo do mes, e o usuario leria isso como erro de conta.
     */
    public int getDias() {
        return dias;
    }
}
