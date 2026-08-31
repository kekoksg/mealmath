package br.com.kelvinsouza.mealmath.controller;

import br.com.kelvinsouza.mealmath.dto.CadastroRequest;
import br.com.kelvinsouza.mealmath.dto.LoginRequest;
import br.com.kelvinsouza.mealmath.dto.TokenResponse;
import br.com.kelvinsouza.mealmath.service.AutenticacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos de autenticacao (RF001/RF002). Sao as unicas rotas liberadas sem token.
 * O controller so recebe a requisicao e dispara a validacao, a regra fica no Service.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AutenticacaoService autenticacaoService;

    public AuthController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    @PostMapping("/registrar")
    public ResponseEntity<TokenResponse> registrar(@RequestBody @Valid CadastroRequest requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(autenticacaoService.cadastrar(requisicao));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest requisicao) {
        return ResponseEntity.ok(autenticacaoService.login(requisicao));
    }
}
