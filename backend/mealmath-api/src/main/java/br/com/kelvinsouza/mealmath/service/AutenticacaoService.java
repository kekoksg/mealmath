package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.domain.exception.CredenciaisInvalidasException;
import br.com.kelvinsouza.mealmath.domain.exception.EmailJaCadastradoException;
import br.com.kelvinsouza.mealmath.dto.CadastroRequest;
import br.com.kelvinsouza.mealmath.dto.LoginRequest;
import br.com.kelvinsouza.mealmath.dto.TokenResponse;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import br.com.kelvinsouza.mealmath.security.TokenService;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro (RF001) e login (RF002). */
@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AutenticacaoService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /** Cria a conta e ja devolve o token, porque o RF001 pede que o cadastro ja autentique. */
    @Transactional
    public TokenResponse cadastrar(CadastroRequest requisicao) {
        String email = normalizarEmail(requisicao.email());

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuario =
                new Usuario(
                        requisicao.nome().trim(), email, passwordEncoder.encode(requisicao.senha()));

        try {
            // saveAndFlush para o erro de e-mail repetido estourar aqui e nao no commit.
            // Duas requisicoes ao mesmo tempo conseguem passar juntas pelo existsBy de cima.
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new EmailJaCadastradoException();
        }

        return tokenService.gerarPara(new UsuarioAutenticado(usuario));
    }

    /** Valida o e-mail e a senha pelo AuthenticationManager, que compara o bcrypt, e gera o token. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest requisicao) {
        try {
            Authentication autenticacao =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    normalizarEmail(requisicao.email()), requisicao.senha()));

            return tokenService.gerarPara((UsuarioAutenticado) autenticacao.getPrincipal());
        } catch (AuthenticationException e) {
            // Junto "e-mail nao existe" e "senha errada" na mesma resposta de proposito.
            throw new CredenciaisInvalidasException();
        }
    }

    private String normalizarEmail(String email) {
        return Usuario.normalizarEmail(email);
    }
}
