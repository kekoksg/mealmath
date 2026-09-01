package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.dto.DuplicarDiaAnteriorRequest;
import br.com.kelvinsouza.mealmath.dto.ItemRegistroQuantidadeRequest;
import br.com.kelvinsouza.mealmath.dto.RegistroDiarioRequest;
import br.com.kelvinsouza.mealmath.dto.RegistroDiarioResponse;
import br.com.kelvinsouza.mealmath.service.RegistroDiarioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Diario de consumo (RF008). Rota protegida, o dono sai do token. */
@RestController
@RequestMapping("/registros-diarios")
public class RegistroDiarioController {

    private final RegistroDiarioService registroDiarioService;

    public RegistroDiarioController(RegistroDiarioService registroDiarioService) {
        this.registroDiarioService = registroDiarioService;
    }

    @PostMapping
    public ResponseEntity<RegistroDiarioResponse> registrar(
            @RequestBody @Valid RegistroDiarioRequest requisicao) {
        RegistroDiarioResponse criado = registroDiarioService.registrar(requisicao);
        return ResponseEntity.created(URI.create("/registros-diarios/" + criado.id())).body(criado);
    }

    /** Busca um dia so (?data=2026-08-03) ou um intervalo (?inicio=2026-08-01&fim=2026-08-31). */
    @GetMapping
    public List<RegistroDiarioResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate data,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fim) {
        return registroDiarioService.listar(data, inicio, fim);
    }

    @GetMapping("/{id}")
    public RegistroDiarioResponse buscar(@PathVariable Long id) {
        return registroDiarioService.buscar(id);
    }

    /** Muda a quantidade so nesse registro, nunca no modelo da biblioteca. */
    @PatchMapping("/{registroId}/itens/{itemId}")
    public RegistroDiarioResponse ajustarItem(
            @PathVariable Long registroId,
            @PathVariable Long itemId,
            @RequestBody @Valid ItemRegistroQuantidadeRequest requisicao) {
        return registroDiarioService.ajustarItem(registroId, itemId, requisicao);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        registroDiarioService.remover(id);
        return ResponseEntity.noContent().build();
    }

    /** Botao de repetir as refeicoes de ontem: copia o dia anterior para a data informada. */
    @PostMapping("/duplicar-dia-anterior")
    public ResponseEntity<List<RegistroDiarioResponse>> duplicarDiaAnterior(
            @RequestBody @Valid DuplicarDiaAnteriorRequest requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registroDiarioService.duplicarDiaAnterior(requisicao));
    }
}
