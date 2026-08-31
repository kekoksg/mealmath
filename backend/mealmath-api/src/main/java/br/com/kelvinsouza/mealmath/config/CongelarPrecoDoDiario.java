package br.com.kelvinsouza.mealmath.config;

import br.com.kelvinsouza.mealmath.domain.BasePreco;
import br.com.kelvinsouza.mealmath.domain.ItemRegistro;
import br.com.kelvinsouza.mealmath.repository.ItemRegistroRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preenche o preco congelado das linhas do diario gravadas antes dessa regra existir. Sem a copia
 * do preco elas continuariam mudando de valor a cada alteracao no item de mercado.
 *
 * Usa o preco de hoje e nao o que valia na data de cada consumo. Da para reconstruir o passado pelo
 * HistoricoPreco, mas isso mudaria agora valores que o usuario ja viu. A intencao era parar de
 * mexer no que esta registrado, nao reescrever os totais antigos.
 *
 * E idempotente: so toca em linha sem a copia. Da para apagar quando nao existir mais base antiga.
 */
@Component
public class CongelarPrecoDoDiario implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CongelarPrecoDoDiario.class);

    private final ItemRegistroRepository itens;

    public CongelarPrecoDoDiario(ItemRegistroRepository itens) {
        this.itens = itens;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ItemRegistro> pendentes = itens.findByPrecoNoConsumoIsNull();
        if (pendentes.isEmpty()) {
            return;
        }

        int congelados = 0;
        int semVinculo = 0;

        for (ItemRegistro item : pendentes) {
            BasePreco base = BasePreco.vigenteDe(item.getItemMercado());
            if (base == null) {
                // Sem item de mercado nao tem preco para congelar. A linha continua fora do
                // total e sinalizada na tela, como manda a regra de item sem preco.
                semVinculo++;
                continue;
            }
            item.congelarPreco(base);
            congelados++;
        }

        log.info(
                "Preço do diário congelado em {} linha(s) anteriores à regra; {} sem item de mercado vinculado.",
                congelados,
                semVinculo);
    }
}
