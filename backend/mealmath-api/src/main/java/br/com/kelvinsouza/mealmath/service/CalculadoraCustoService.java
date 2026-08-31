package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.BasePreco;
import br.com.kelvinsouza.mealmath.domain.ItemConsumido;
import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.domain.UnidadeMedida;
import br.com.kelvinsouza.mealmath.domain.exception.ItemSemPrecoException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Calcula o custo da porcao consumida de cada alimento (RF005).
 *
 * custoUnitario = preco / embalagem convertida para a unidade base
 * custoItem     = custoUnitario * quantidade consumida convertida para a unidade base
 * custoRefeicao = soma dos custoItem
 *
 * Nenhum valor e arredondado no meio do caminho: a soma sai com a escala cheia e quem arredonda
 * para 2 casas e o front. Arredondando item por item o total sai errado.
 */
@Service
public class CalculadoraCustoService {

    // Precisao usada so na divisao do custo unitario, que e a unica conta aqui que pode nao
    // terminar. Sem definir isso o BigDecimal.divide lanca ArithmeticException em casos como
    // R$ 10,00 dividido por 3 un. Sao 34 digitos, entao o erro e muito menor que um centavo.
    private static final MathContext PRECISAO_DIVISAO = MathContext.DECIMAL128;

    private final ConversorUnidadeService conversor;

    public CalculadoraCustoService(ConversorUnidadeService conversor) {
        this.conversor = conversor;
    }

    /**
     * Custo de 1 unidade base do item (1 g, 1 mL ou 1 un).
     *
     * Nao fica salvo no ItemMercado de proposito. Sendo sempre derivado do preco atual, a
     * atualizacao de preco (RF007) ja chega certa em todos os calculos, sem recalculo em massa.
     */
    public BigDecimal custoUnitario(ItemMercado item) {
        if (item == null) {
            throw new IllegalArgumentException("Item de mercado é obrigatório.");
        }
        return custoUnitarioDe(
                item.getPreco(), item.getQuantidadeEmbalagem(), item.getUnidade());
    }

    /**
     * Mesma formula, mas recebendo os valores soltos, para quem nao tem um ItemMercado na mao.
     * E o caso do HistoricoPreco, que guarda um preco antigo e precisa do custo unitario daquela
     * epoca para a comparacao do dashboard (RF006/RF007).
     */
    public BigDecimal custoUnitarioDe(
            BigDecimal preco, BigDecimal quantidadeEmbalagem, UnidadeMedida unidade) {
        conversor.exigirPositivo(preco, "Preço do item de mercado");
        BigDecimal embalagemNaBase =
                conversor.paraBase(quantidadeEmbalagem, unidade, "Quantidade da embalagem");

        return preco.divide(embalagemNaBase, PRECISAO_DIVISAO);
    }

    /**
     * Custo da porcao consumida de um item.
     *
     * Lanca ItemSemPrecoException quando nao existe preco. Quem calcula uma refeicao inteira deve
     * usar o metodo calcular, que tira o item do total em vez de quebrar.
     */
    public BigDecimal custoItem(ItemConsumido item) {
        if (item == null) {
            throw new IllegalArgumentException("Item consumido é obrigatório.");
        }
        // Quem decide o preco e a base: atual na biblioteca e congelado no diario. Lendo
        // o ItemMercado direto aqui, mudar o preco reescreveria dias que ja passaram.
        BasePreco base = item.getBasePreco();
        if (base == null) {
            throw new ItemSemPrecoException(item.getDescricao());
        }

        // A comparacao usa a grandeza da embalagem que valeu nessa linha, e nao com a do item hoje.
        // Assim, se o item mudou de grandeza depois, o registro antigo continua batendo.
        conversor.exigirMesmaGrandeza(base.unidade(), item.getUnidade());
        BigDecimal consumoNaBase =
                conversor.paraBase(
                        item.getQuantidadeConsumida(), item.getUnidade(), "Quantidade consumida");

        return custoUnitarioDe(base.preco(), base.quantidadeEmbalagem(), base.unidade())
                .multiply(consumoNaBase);
    }

    /**
     * Soma o custo de uma lista de itens consumidos e separa os que nao deu para calcular por falta
     * de preco. Esses saem do total e voltam na lista itensSemPreco, nunca contam como R$ 0,00.
     */
    public ResultadoCusto calcular(Collection<? extends ItemConsumido> itens) {
        if (itens == null || itens.isEmpty()) {
            return new ResultadoCusto(BigDecimal.ZERO, List.of(), List.of());
        }

        List<CustoItem> detalhes = new ArrayList<>();
        List<String> semPreco = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (ItemConsumido item : itens) {
            if (item.getBasePreco() == null) {
                semPreco.add(item.getDescricao());
                continue;
            }
            BigDecimal custo = custoItem(item);
            detalhes.add(new CustoItem(item.getDescricao(), custo));
            total = total.add(custo);
        }

        return new ResultadoCusto(total, detalhes, semPreco);
    }

    /** Custo de uma refeicao modelo da biblioteca, para a previa do RF003/RF005. */
    public ResultadoCusto calcularRefeicao(Refeicao refeicao) {
        if (refeicao == null) {
            throw new IllegalArgumentException("Refeição é obrigatória.");
        }
        return calcular(refeicao.getItens());
    }

    /** Custo de uma refeicao que foi consumida de verdade. E esse que vai para o dashboard (RF006). */
    public ResultadoCusto calcularRegistro(RegistroDiario registro) {
        if (registro == null) {
            throw new IllegalArgumentException("Registro diário é obrigatório.");
        }
        return calcular(registro.getItens());
    }
}
