package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.dto.RefeicaoRequest;
import br.com.kelvinsouza.mealmath.dto.RefeicaoResponse;
import br.com.kelvinsouza.mealmath.service.RefeicaoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Biblioteca de refeicoes (RF003). Rota protegida, o dono sai do token. */
@RestController
@RequestMapping("/refeicoes")
public class RefeicaoController {

    private final RefeicaoService refeicaoService;

    public RefeicaoController(RefeicaoService refeicaoService) {
        this.refeicaoService = refeicaoService;
    }

    @PostMapping
    public ResponseEntity<RefeicaoResponse> criar(@RequestBody @Valid RefeicaoRequest requisicao) {
        RefeicaoResponse criada = refeicaoService.criar(requisicao);
        return ResponseEntity.created(URI.create("/refeicoes/" + criada.id())).body(criada);
    }

    @GetMapping
    public List<RefeicaoResponse> listar() {
        return refeicaoService.listar();
    }

    @GetMapping("/{id}")
    public RefeicaoResponse buscar(@PathVariable Long id) {
        return refeicaoService.buscar(id);
    }

    @PutMapping("/{id}")
    public RefeicaoResponse atualizar(
            @PathVariable Long id, @RequestBody @Valid RefeicaoRequest requisicao) {
        return refeicaoService.atualizar(id, requisicao);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        refeicaoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
