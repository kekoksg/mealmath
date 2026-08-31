package br.com.kelvinsouza.mealmath.config;

import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.RegistroDiario;
import br.com.kelvinsouza.mealmath.repository.RefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Troca o emoji salvo em refeicao.icone e registro_diario.icone pelo nome do icone equivalente do
 * Solar Icon Set, senao a tela ficaria com dois estilos misturados.
 *
 * O pacote tem poucos icones de comida, entao varios emojis caem no mesmo: macarrao, arroz e prato
 * viram todos "almoco". A alternativa seria inventar icone que o pacote nao tem.
 *
 * E idempotente: so toca em linha cujo icone ainda nao tem o prefixo novo. Emoji fora do mapa vira
 * o padrao. Da para apagar quando nao existir mais base antiga.
 */
@Component
public class MigrarIconesParaSolar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrarIconesParaSolar.class);

    /** Prefixo dos nomes novos. E ele que marca o que ja foi migrado. */
    private static final String PREFIXO_NOVO = "ref-";

    private static final String PADRAO = "ref-almoco";

    private static final Map<String, String> EQUIVALENTE =
            Map.ofEntries(
                    Map.entry("☀️", "ref-manha"),
                    Map.entry("🍽️", "ref-almoco"),
                    Map.entry("🥗", "ref-salada"),
                    Map.entry("🍎", "ref-salada"),
                    Map.entry("🌙", "ref-jantar"),
                    Map.entry("🥣", "ref-manha"),
                    Map.entry("🍳", "ref-caseiro"),
                    Map.entry("🥪", "ref-marmita"),
                    Map.entry("🍜", "ref-almoco"),
                    Map.entry("🍛", "ref-almoco"),
                    Map.entry("🥤", "ref-bebida"),
                    Map.entry("🍌", "ref-salada"));

    private final RefeicaoRepository refeicoes;
    private final RegistroDiarioRepository registros;

    public MigrarIconesParaSolar(RefeicaoRepository refeicoes, RegistroDiarioRepository registros) {
        this.refeicoes = refeicoes;
        this.registros = registros;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Refeicao> modelos = refeicoes.findByIconeNotLike(PREFIXO_NOVO + "%");
        List<RegistroDiario> dias = registros.findByIconeNotLike(PREFIXO_NOVO + "%");

        if (modelos.isEmpty() && dias.isEmpty()) {
            return;
        }

        modelos.forEach(modelo -> modelo.setIcone(equivalenteDe(modelo.getIcone())));
        dias.forEach(dia -> dia.setIcone(equivalenteDe(dia.getIcone())));

        log.info(
                "Ícones migrados para o Solar: {} refeição(ões) e {} registro(s) do diário.",
                modelos.size(),
                dias.size());
    }

    private String equivalenteDe(String emoji) {
        return emoji == null ? PADRAO : EQUIVALENTE.getOrDefault(emoji, PADRAO);
    }
}
