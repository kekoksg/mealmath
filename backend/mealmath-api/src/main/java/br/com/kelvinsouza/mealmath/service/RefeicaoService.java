package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.ItemRefeicao;
import br.com.kelvinsouza.mealmath.domain.Refeicao;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.domain.exception.ItemMercadoInativoException;
import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import br.com.kelvinsouza.mealmath.dto.ItemRefeicaoRequest;
import br.com.kelvinsouza.mealmath.dto.ItemRefeicaoResponse;
import br.com.kelvinsouza.mealmath.dto.RefeicaoRequest;
import br.com.kelvinsouza.mealmath.dto.RefeicaoResponse;
import br.com.kelvinsouza.mealmath.repository.ItemMercadoRepository;
import br.com.kelvinsouza.mealmath.repository.RefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.RegistroDiarioRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticadoProvider;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca de refeicoes modelo (RF003), com o custo vindo da CalculadoraCustoService (RF005).
 *
 * Nada aqui grava consumo. Essas refeicoes sao so modelos reutilizaveis. O que foi comido em um dia
 * fica no RegistroDiario, que tem copia propria dos itens.
 */
@Service
public class RefeicaoService {

    private final RefeicaoRepository refeicaoRepository;
    private final ItemMercadoRepository itemMercadoRepository;
    private final RegistroDiarioRepository registroDiarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalculadoraCustoService calculadora;
    private final ConversorUnidadeService conversor;
    private final UsuarioAutenticadoProvider usuarioAutenticado;

    public RefeicaoService(
            RefeicaoRepository refeicaoRepository,
            ItemMercadoRepository itemMercadoRepository,
            RegistroDiarioRepository registroDiarioRepository,
            UsuarioRepository usuarioRepository,
            CalculadoraCustoService calculadora,
            ConversorUnidadeService conversor,
            UsuarioAutenticadoProvider usuarioAutenticado) {
        this.refeicaoRepository = refeicaoRepository;
        this.itemMercadoRepository = itemMercadoRepository;
        this.registroDiarioRepository = registroDiarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.calculadora = calculadora;
        this.conversor = conversor;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @Transactional
    public RefeicaoResponse criar(RefeicaoRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        Usuario dono = usuarioRepository.getReferenceById(usuarioId);

        Refeicao refeicao = new Refeicao(dono, requisicao.titulo().trim(), requisicao.icone());
        aplicarItens(refeicao, requisicao.itens(), usuarioId, Set.of());

        return montarResposta(refeicaoRepository.save(refeicao));
    }

    /**
     * Lista a biblioteca. A consulta usa @EntityGraph com itens e itens.itemMercado, entao os itens
     * e os precos vem no mesmo SELECT. Sem isso, calcular o custo de N refeicoes dispararia 2N
     * consultas a mais (problema do N+1).
     */
    @Transactional(readOnly = true)
    public List<RefeicaoResponse> listar() {
        return refeicaoRepository
                .findByUsuarioIdOrderByTituloAsc(usuarioAutenticado.idDoUsuarioAutenticado())
                .stream()
                .map(this::montarResposta)
                .toList();
    }

    @Transactional(readOnly = true)
    public RefeicaoResponse buscar(Long id) {
        return montarResposta(buscarDoUsuario(id));
    }

    /** A lista de itens que vem no corpo substitui a composicao inteira. O que nao vier e apagado. */
    @Transactional
    public RefeicaoResponse atualizar(Long id, RefeicaoRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        Refeicao refeicao = buscarDoUsuario(id);

        // Itens que ja estavam na refeicao podem continuar mesmo desativados, senao desativar um
        // item travaria a edicao de todas as refeicoes que ja usavam ele.
        Set<Long> jaVinculados =
                refeicao.getItens().stream()
                        .map(item -> item.getItemMercado().getId())
                        .collect(Collectors.toSet());

        refeicao.setTitulo(requisicao.titulo().trim());
        refeicao.setIcone(requisicao.icone());
        refeicao.limparItens();
        aplicarItens(refeicao, requisicao.itens(), usuarioId, jaVinculados);

        return montarResposta(refeicao);
    }

    /**
     * Aqui a exclusao e de verdade, porque a biblioteca e modelo e nao historico.
     *
     * Antes de apagar, o vinculo de rastreio dos registros do diario e removido. Eles tem copia propria
     * dos itens e precisam continuar iguais depois que o modelo some.
     */
    @Transactional
    public void excluir(Long id) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();

        if (!refeicaoRepository.existsByIdAndUsuarioId(id, usuarioId)) {
            throw new RecursoNaoEncontradoException("Refeição");
        }

        // A ordem importa. Desvinculando primeiro, o contexto de persistencia e limpo e nenhum
        // RegistroDiario fica na memoria apontando para a refeicao que vai ser apagada. Por isso
        // a entidade so e carregada depois, pelo deleteById.
        registroDiarioRepository.desvincularRefeicaoOrigem(id, usuarioId);
        refeicaoRepository.deleteById(id);
    }

    /**
     * Monta as linhas da refeicao conferindo o dono, se o item esta ativo e se a unidade e
     * compativel. O parametro idsTolerados sao os itens que ja estavam na refeicao e podem ficar
     * mesmo desativados.
     */
    private void aplicarItens(
            Refeicao refeicao,
            List<ItemRefeicaoRequest> linhas,
            Long usuarioId,
            Set<Long> idsTolerados) {

        for (ItemRefeicaoRequest linha : linhas) {
            // Filtro por usuario. Sem isso, mandando o id de um item de outra pessoa daria para
            // montar uma refeicao com o preco de outra conta.
            ItemMercado item =
                    itemMercadoRepository
                            .findByIdAndUsuarioId(linha.itemMercadoId(), usuarioId)
                            .orElseThrow(
                                    () -> new RecursoNaoEncontradoException("Item de mercado"));

            if (!item.isAtivo() && !idsTolerados.contains(item.getId())) {
                throw new ItemMercadoInativoException(item.getNome());
            }

            // Bloqueia consumir em g um item vendido em L. Valido aqui e nao na hora do calculo.
            conversor.exigirMesmaGrandeza(item.getUnidade(), linha.unidade());

            refeicao.adicionarItem(
                    new ItemRefeicao(item, linha.quantidadeConsumida(), linha.unidade()));
        }
    }

    private Refeicao buscarDoUsuario(Long id) {
        return refeicaoRepository
                .findByIdAndUsuarioId(id, usuarioAutenticado.idDoUsuarioAutenticado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Refeição"));
    }

    private RefeicaoResponse montarResposta(Refeicao refeicao) {
        List<ItemRefeicaoResponse> itens =
                refeicao.getItens().stream()
                        .map(item -> ItemRefeicaoResponse.de(item, calculadora.custoItem(item)))
                        .toList();

        return new RefeicaoResponse(
                refeicao.getId(),
                refeicao.getTitulo(),
                refeicao.getIcone(),
                itens,
                calculadora.calcularRefeicao(refeicao).total());
    }
}
