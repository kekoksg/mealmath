package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.dto.HistoricoPrecoResponse;
import br.com.kelvinsouza.mealmath.dto.ItemMercadoRequest;
import br.com.kelvinsouza.mealmath.dto.ItemMercadoResponse;
import br.com.kelvinsouza.mealmath.service.ItemMercadoService;
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

/**
 * Itens de mercado (RF004/RF007). Rota protegida: precisa de token e o dono sai do proprio token.
 * Nenhum endpoint daqui aceita id de usuario como parametro.
 */
@RestController
@RequestMapping("/itens-mercado")
public class ItemMercadoController {

    private final ItemMercadoService itemMercadoService;

    public ItemMercadoController(ItemMercadoService itemMercadoService) {
        this.itemMercadoService = itemMercadoService;
    }

    @PostMapping
    public ResponseEntity<ItemMercadoResponse> criar(
            @RequestBody @Valid ItemMercadoRequest requisicao) {
        ItemMercadoResponse criado = itemMercadoService.criar(requisicao);
        return ResponseEntity.created(URI.create("/itens-mercado/" + criado.id())).body(criado);
    }

    @GetMapping
    public List<ItemMercadoResponse> listar() {
        return itemMercadoService.listar();
    }

    @GetMapping("/{id}")
    public ItemMercadoResponse buscar(@PathVariable Long id) {
        return itemMercadoService.buscar(id);
    }

    @PutMapping("/{id}")
    public ItemMercadoResponse atualizar(
            @PathVariable Long id, @RequestBody @Valid ItemMercadoRequest requisicao) {
        return itemMercadoService.atualizar(id, requisicao);
    }

    /** Exclusao logica: o item some da lista, mas o diario continua conseguindo calcular o custo. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        itemMercadoService.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoPrecoResponse> historico(@PathVariable Long id) {
        return itemMercadoService.historico(id);
    }
}
