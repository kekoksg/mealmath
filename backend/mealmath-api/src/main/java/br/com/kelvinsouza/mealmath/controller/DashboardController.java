package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.domain.PeriodoDashboard;
import br.com.kelvinsouza.mealmath.dto.DashboardResponse;
import br.com.kelvinsouza.mealmath.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custo consolidado por periodo (RF006). Rota protegida, o dono sai do token.
 *
 * A rota nao tem id porque so existe um dashboard, o do usuario logado, e ela e so de leitura.
 * Todo o calculo fica no DashboardService, aqui so passa a escolha do periodo.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

        /**
         * O padrao e SEMANA porque e a aba ja selecionada na tela, e um dia sozinho costuma ser pouco
         * para o grafico de composicao mostrar alguma coisa.
         *
         * Responde 200 mesmo sem registro no periodo: periodo vazio e estado normal de quem acabou de
         * criar a conta e nao e erro.
         */
    @GetMapping
    public DashboardResponse consolidar(
            @RequestParam(defaultValue = "SEMANA") PeriodoDashboard periodo) {
        return dashboardService.consolidar(periodo);
    }
}
