package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.dto.AlterarSenhaRequest;
import br.com.kelvinsouza.mealmath.dto.PerfilRequest;
import br.com.kelvinsouza.mealmath.dto.TokenResponse;
import br.com.kelvinsouza.mealmath.dto.UsuarioResponse;
import br.com.kelvinsouza.mealmath.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dados da conta do usuario logado. Igual a meta, a rota nao leva id: o dono sai do token.
 */
@RestController
@RequestMapping("/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    public ResponseEntity<UsuarioResponse> buscar() {
        return ResponseEntity.ok(perfilService.buscar());
    }

    /** Devolve um token novo, porque o nome e o e-mail ficam dentro do JWT. */
    @PutMapping
    public ResponseEntity<TokenResponse> atualizar(@RequestBody @Valid PerfilRequest requisicao) {
        return ResponseEntity.ok(perfilService.atualizar(requisicao));
    }

    @PutMapping("/senha")
    public ResponseEntity<TokenResponse> alterarSenha(
            @RequestBody @Valid AlterarSenhaRequest requisicao) {
        return ResponseEntity.ok(perfilService.alterarSenha(requisicao));
    }
}
