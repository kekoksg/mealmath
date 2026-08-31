package br.com.kelvinsouza.mealmath.dto;

import br.com.kelvinsouza.mealmath.domain.PeriodoDashboard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todos os dados da tela de dashboard (RF006) em uma requisicao so.
 *
 * Os campos comparativo e meta vem nulos quando nao existe periodo anterior com gasto ou meta
 * definida. Nulo e nao zero, de proposito: a tela esconde o indicador em vez de mostrar um
 * numero inventado.
 */
public record DashboardResponse(
        PeriodoDashboard periodo,
        LocalDate inicio,
        LocalDate fim,
        BigDecimal custoTotal,
        BigDecimal custoMedioPorDia,
        ComparativoPeriodoResponse comparativo,
        CompletudeDiarioResponse completude,
        ProgressoMetaResponse meta,
        List<ComposicaoCategoriaResponse> composicaoPorCategoria,
        List<ItemImpactoResponse> itensMaiorImpacto,
        List<AltaPrecoResponse> altasDePreco,
        List<String> itensSemPreco) {

    public DashboardResponse {
        composicaoPorCategoria = List.copyOf(composicaoPorCategoria);
        itensMaiorImpacto = List.copyOf(itensMaiorImpacto);
        altasDePreco = List.copyOf(altasDePreco);
        itensSemPreco = List.copyOf(itensSemPreco);
    }

        /**
         * Cuidado para nao ler como "o usuario nao registrou nada": um dia registrado so com itens sem
         * preco tambem cai aqui, e nesse caso quem explica o zero e a lista itensSemPreco.
         */
    public boolean semDados() {
        return custoTotal.signum() == 0;
    }
}
