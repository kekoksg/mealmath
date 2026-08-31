package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.MetaOrcamento;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import br.com.kelvinsouza.mealmath.dto.MetaOrcamentoRequest;
import br.com.kelvinsouza.mealmath.dto.MetaOrcamentoResponse;
import br.com.kelvinsouza.mealmath.repository.MetaOrcamentoRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticadoProvider;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD da meta de orcamento (RF010). O dashboard (RF006) usa ela para montar a barra de progresso.
 *
 * A meta e unica por usuario e sempre buscada pelo id que vem do token. Nenhum metodo daqui recebe
 * id de meta ou de usuario por parametro, entao nao tem como alguem trocar o id na URL e chegar na
 * meta de outra pessoa.
 */
@Service
public class MetaOrcamentoService {

    private final MetaOrcamentoRepository metaOrcamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioAutenticadoProvider usuarioAutenticado;

    public MetaOrcamentoService(
            MetaOrcamentoRepository metaOrcamentoRepository,
            UsuarioRepository usuarioRepository,
            UsuarioAutenticadoProvider usuarioAutenticado) {
        this.metaOrcamentoRepository = metaOrcamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    /**
     * Devolver Optional vazio e resposta normal e nao erro: quer dizer que o usuario nao definiu
     * meta ainda, e o dashboard esconde o progresso em vez de mostrar 0%.
     */
    @Transactional(readOnly = true)
    public Optional<MetaOrcamentoResponse> buscar() {
        return metaOrcamentoRepository
                .findByUsuarioId(usuarioAutenticado.idDoUsuarioAutenticado())
                .map(MetaOrcamentoResponse::de);
    }

    /**
     * Como so existe uma meta por usuario, definir sobrescreve a que ja existia em vez de criar
     * outra linha (RF010). Trocar o periodo tambem e so uma atualizacao: nao da para ter uma meta
     * semanal e uma mensal ao mesmo tempo.
     */
    @Transactional
    public ResultadoDefinicaoMeta definir(MetaOrcamentoRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        Optional<MetaOrcamento> existente = metaOrcamentoRepository.findByUsuarioId(usuarioId);

        if (existente.isPresent()) {
            MetaOrcamento meta = existente.get();
            meta.setValor(requisicao.valor());
            meta.setPeriodo(requisicao.periodo());
            // Faco o flush na mao para o @UpdateTimestamp rodar antes. Sem isso a resposta saia
            // com o atualizadoEm velho, porque o record e montado antes do flush do commit.
            return new ResultadoDefinicaoMeta(
                    MetaOrcamentoResponse.de(metaOrcamentoRepository.saveAndFlush(meta)), false);
        }

        // O getReferenceById evita um SELECT so para preencher a chave estrangeira do dono.
        Usuario dono = usuarioRepository.getReferenceById(usuarioId);
        MetaOrcamento criada =
                metaOrcamentoRepository.save(
                        new MetaOrcamento(dono, requisicao.periodo(), requisicao.valor()));

        return new ResultadoDefinicaoMeta(MetaOrcamentoResponse.de(criada), true);
    }

    /**
     * Apaga a meta de verdade do banco. Aqui pode, diferente do item de mercado, porque nada aponta
     * para a meta: o dashboard so volta a esconder a barra de progresso.
     */
    @Transactional
    public void remover() {
        MetaOrcamento meta =
                metaOrcamentoRepository
                        .findByUsuarioId(usuarioAutenticado.idDoUsuarioAutenticado())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Meta de orçamento"));

        metaOrcamentoRepository.delete(meta);
    }
}
