package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.dto.MetaOrcamentoRequest;
import br.com.kelvinsouza.mealmath.dto.MetaOrcamentoResponse;
import br.com.kelvinsouza.mealmath.service.MetaOrcamentoService;
import br.com.kelvinsouza.mealmath.service.ResultadoDefinicaoMeta;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta de orcamento (RF009). Como cada usuario tem no maximo uma meta, a rota nao leva id: o dono
 * sai do token e a URL e a mesma para todo mundo.
 */
@RestController
@RequestMapping("/meta-orcamento")
public class MetaOrcamentoController {

    private final MetaOrcamentoService metaOrcamentoService;

    public MetaOrcamentoController(MetaOrcamentoService metaOrcamentoService) {
        this.metaOrcamentoService = metaOrcamentoService;
    }

    /**
     * Devolve 204 quando o usuario ainda nao definiu meta. 204 e nao 404 porque "ainda nao
     * definiu" e situacao normal e nao erro. Como o 204 chega no Angular com corpo nulo, a tela ja
     * cai no estado vazio com o botao "Definir meta", sem passar pelo tratamento de erro.
     */
    @GetMapping
    public ResponseEntity<MetaOrcamentoResponse> buscar() {
        return metaOrcamentoService
                .buscar()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * PUT porque a operacao sobrescreve a meta inteira e pode ser repetida sem mudar o
     * resultado. Devolve 201 na primeira vez e 200 na atualizacao, e o front usa isso para escolher
     * entre "Meta definida" e "Meta atualizada".
     */
    @PutMapping
    public ResponseEntity<MetaOrcamentoResponse> definir(
            @RequestBody @Valid MetaOrcamentoRequest requisicao) {
        ResultadoDefinicaoMeta resultado = metaOrcamentoService.definir(requisicao);

        return resultado.criada()
                ? ResponseEntity.created(URI.create("/meta-orcamento")).body(resultado.meta())
                : ResponseEntity.ok(resultado.meta());
    }

    /** Apaga a meta. O dashboard volta a esconder o progresso e a oferecer o botao de definir. */
    @DeleteMapping
    public ResponseEntity<Void> remover() {
        metaOrcamentoService.remover();
        return ResponseEntity.noContent().build();
    }
}
