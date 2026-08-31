package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.domain.exception.EmailJaCadastradoException;
import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import br.com.kelvinsouza.mealmath.domain.exception.SenhaAtualIncorretaException;
import br.com.kelvinsouza.mealmath.dto.AlterarSenhaRequest;
import br.com.kelvinsouza.mealmath.dto.PerfilRequest;
import br.com.kelvinsouza.mealmath.dto.TokenResponse;
import br.com.kelvinsouza.mealmath.dto.UsuarioResponse;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import br.com.kelvinsouza.mealmath.security.TokenService;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticado;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticadoProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados da propria conta. Sempre trabalha em cima do usuario do token, nao existe rota para editar
 * o perfil de outro id.
 */
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public PerfilService(
            UsuarioRepository usuarioRepository,
            UsuarioAutenticadoProvider usuarioAutenticadoProvider,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar() {
        return UsuarioResponse.de(usuarioAtual());
    }

    /**
     * Devolve um token novo e nao so o usuario, porque o nome e o e-mail ficam dentro do JWT.
     * Mantendo o token antigo, a tela continuaria mostrando o dado velho ate o proximo login.
     */
    @Transactional
    public TokenResponse atualizar(PerfilRequest requisicao) {
        Usuario usuario = usuarioAtual();
        String email = Usuario.normalizarEmail(requisicao.email());

        // O proprio e-mail do usuario nao conta como duplicado. Sem essa comparacao, salvar o
        // perfil sem mudar o e-mail ja devolveria 409.
        if (!email.equalsIgnoreCase(usuario.getEmail())
                && usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailJaCadastradoException();
        }

        usuario.setNome(requisicao.nome().trim());
        usuario.setEmail(email);

        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new EmailJaCadastradoException();
        }

        return tokenService.gerarPara(new UsuarioAutenticado(usuario));
    }

    @Transactional
    public TokenResponse alterarSenha(AlterarSenhaRequest requisicao) {
        Usuario usuario = usuarioAtual();

        if (!passwordEncoder.matches(requisicao.senhaAtual(), usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }

        usuario.setSenhaHash(passwordEncoder.encode(requisicao.novaSenha()));
        usuarioRepository.save(usuario);

        return tokenService.gerarPara(new UsuarioAutenticado(usuario));
    }

    private Usuario usuarioAtual() {
        Long id = usuarioAutenticadoProvider.idDoUsuarioAutenticado();
        return usuarioRepository
                .findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário"));
    }
}
