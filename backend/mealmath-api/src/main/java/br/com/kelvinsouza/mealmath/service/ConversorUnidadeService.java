package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.exception.GrandezaIncompativelException;
import br.com.kelvinsouza.mealmath.domain.exception.QuantidadeInvalidaException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Converte as quantidades para a unidade base de cada grandeza: massa vira g, volume vira mL e
 * contagem vira un.
 *
 * Nada e comparado ou somado sem passar pelo paraBase, porque 1 kg e 1000 g sao a mesma coisa e
 * tem que dar o mesmo custo.
 */
@Service
public class ConversorUnidadeService {

        /** A multiplicacao e exata: arredondar ja na conversao estragaria todo o resto do calculo. */
    public BigDecimal paraBase(BigDecimal quantidade, UnidadeMedida unidade) {
        return paraBase(quantidade, unidade, "Quantidade");
    }

    /** Mesma conversao, com o nome do campo para aparecer na mensagem de erro. */
    public BigDecimal paraBase(BigDecimal quantidade, UnidadeMedida unidade, String nomeCampo) {
        if (unidade == null) {
            throw new IllegalArgumentException("Unidade de medida é obrigatória.");
        }
        exigirPositivo(quantidade, nomeCampo);
        return quantidade.multiply(unidade.getFatorParaBase());
    }

    /**
     * Bloqueia unidades de grandezas diferentes. Consumir em g um item vendido em L e entrada
     * invalida, nao e uma conta para tentar fazer.
     */
    public void exigirMesmaGrandeza(UnidadeMedida embalagem, UnidadeMedida consumo) {
        if (embalagem == null || consumo == null) {
            throw new IllegalArgumentException("Unidade de medida é obrigatória.");
        }
        if (!embalagem.compativelCom(consumo)) {
            throw new GrandezaIncompativelException(embalagem, consumo);
        }
    }

    /** Confere se o valor e maior que zero. Usado tambem fora da conversao, no preco por exemplo. */
    public void exigirPositivo(BigDecimal valor, String nomeCampo) {
        if (valor == null || valor.signum() <= 0) {
            throw new QuantidadeInvalidaException(nomeCampo, valor);
        }
    }
}
