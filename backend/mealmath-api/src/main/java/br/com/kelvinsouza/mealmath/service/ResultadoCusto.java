package br.com.kelvinsouza.mealmath.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do calculo de uma refeicao (RF005).
 *
 * Os itens sem item de mercado vinculado ficam de fora do total e vao em itensSemPreco, para a
 * tela avisar que o valor mostrado esta incompleto.
 */
public record ResultadoCusto(BigDecimal total, List<CustoItem> itens, List<String> itensSemPreco) {

    public ResultadoCusto {
        itens = List.copyOf(itens);
        itensSemPreco = List.copyOf(itensSemPreco);
    }

    /** Avisa que o total esta incompleto e a tela precisa mostrar o alerta (RF005). */
    public boolean possuiItensSemPreco() {
        return !itensSemPreco.isEmpty();
    }
}
