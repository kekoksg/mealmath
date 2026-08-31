package br.com.kelvinsouza.mealmath.service;

import br.com.kelvinsouza.mealmath.domain.HistoricoPreco;
import br.com.kelvinsouza.mealmath.domain.ItemMercado;
import br.com.kelvinsouza.mealmath.domain.Usuario;
import br.com.kelvinsouza.mealmath.domain.exception.ItemMercadoDuplicadoException;
import br.com.kelvinsouza.mealmath.domain.exception.ItemMercadoEmUsoException;
import br.com.kelvinsouza.mealmath.domain.exception.RecursoNaoEncontradoException;
import br.com.kelvinsouza.mealmath.dto.HistoricoPrecoResponse;
import br.com.kelvinsouza.mealmath.dto.ItemMercadoRequest;
import br.com.kelvinsouza.mealmath.dto.ItemMercadoResponse;
import br.com.kelvinsouza.mealmath.repository.HistoricoPrecoRepository;
import br.com.kelvinsouza.mealmath.repository.ItemMercadoRepository;
import br.com.kelvinsouza.mealmath.repository.ItemRefeicaoRepository;
import br.com.kelvinsouza.mealmath.repository.ItemRegistroRepository;
import br.com.kelvinsouza.mealmath.repository.UsuarioRepository;
import br.com.kelvinsouza.mealmath.security.UsuarioAutenticadoProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD dos itens de mercado (RF004) e atualizacao de preco com historico (RF007).
 *
 * O id do usuario sempre vem do token e nunca do corpo da requisicao ou da URL. Toda chamada de
 * repositorio aqui embaixo leva esse id junto, entao nao existe caminho nesse service que leia um
 * item sem filtrar pelo dono.
 */
@Service
public class ItemMercadoService {

    private final ItemMercadoRepository itemMercadoRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final ItemRefeicaoRepository itemRefeicaoRepository;
    private final ItemRegistroRepository itemRegistroRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalculadoraCustoService calculadora;
    private final UsuarioAutenticadoProvider usuarioAutenticado;

    public ItemMercadoService(
            ItemMercadoRepository itemMercadoRepository,
            HistoricoPrecoRepository historicoPrecoRepository,
            ItemRefeicaoRepository itemRefeicaoRepository,
            ItemRegistroRepository itemRegistroRepository,
            UsuarioRepository usuarioRepository,
            CalculadoraCustoService calculadora,
            UsuarioAutenticadoProvider usuarioAutenticado) {
        this.itemMercadoRepository = itemMercadoRepository;
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.itemRefeicaoRepository = itemRefeicaoRepository;
        this.itemRegistroRepository = itemRegistroRepository;
        this.usuarioRepository = usuarioRepository;
        this.calculadora = calculadora;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @Transactional
    public ItemMercadoResponse criar(ItemMercadoRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        String nome = requisicao.nome().trim();

        if (itemMercadoRepository.existsByUsuarioIdAndNomeIgnoreCaseAndAtivoTrue(usuarioId, nome)) {
            throw new ItemMercadoDuplicadoException(nome);
        }

        // O getReferenceById evita um SELECT so para preencher a chave estrangeira do dono.
        Usuario dono = usuarioRepository.getReferenceById(usuarioId);
        ItemMercado item =
                new ItemMercado(
                        dono,
                        nome,
                        requisicao.preco(),
                        requisicao.quantidadeEmbalagem(),
                        requisicao.unidade(),
                        requisicao.categoriaOuPadrao());

        return montarResposta(itemMercadoRepository.save(item));
    }

    /** Lista so os itens ativos. Os desativados continuam no banco por causa do diario. */
    @Transactional(readOnly = true)
    public List<ItemMercadoResponse> listar() {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();

        return itemMercadoRepository.findByUsuarioIdAndAtivoTrueOrderByNomeAsc(usuarioId).stream()
                .map(this::montarResposta)
                .toList();
    }

    /** Aqui os desativados entram tambem, porque o diario aponta para eles e precisa mostrar. */
    @Transactional(readOnly = true)
    public ItemMercadoResponse buscar(Long id) {
        return montarResposta(buscarDoUsuario(id));
    }

    /**
     * Atualiza o item e, quando o preco muda, salva o valor antigo no HistoricoPreco antes de
     * sobrescrever (RF007).
     *
     * Nao ha recalculo em cascata. Como o custo e sempre derivado do preco atual do item, as
     * refeicoes e o diario ja aparecem com o preco novo na proxima leitura, sem varrer tabela
     * nenhuma.
     */
    @Transactional
    public ItemMercadoResponse atualizar(Long id, ItemMercadoRequest requisicao) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        ItemMercado item = buscarDoUsuario(id);
        String nome = requisicao.nome().trim();

        itemMercadoRepository
                .findByUsuarioIdAndNomeIgnoreCaseAndAtivoTrue(usuarioId, nome)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ItemMercadoDuplicadoException(nome);
                });

        if (item.getUnidade().getGrandeza() != requisicao.unidade().getGrandeza()
                && estaEmUso(id, usuarioId)) {
            throw new ItemMercadoEmUsoException(
                    item.getNome(),
                    item.getUnidade().getGrandeza(),
                    requisicao.unidade().getGrandeza());
        }

        if (baseDePrecoMudou(item, requisicao)) {
            // Grava os valores ANTIGOS. Tem que ser antes dos setters logo abaixo.
            historicoPrecoRepository.save(
                    new HistoricoPreco(
                            item,
                            item.getPreco(),
                            item.getQuantidadeEmbalagem(),
                            item.getUnidade()));
        }

        item.setNome(nome);
        item.setPreco(requisicao.preco());
        item.setQuantidadeEmbalagem(requisicao.quantidadeEmbalagem());
        item.setUnidade(requisicao.unidade());
        // Trocar a categoria nao gera historico, porque ela nao entra no custo unitario. So muda
        // o item de fatia no grafico do dashboard.
        item.setCategoria(requisicao.categoriaOuPadrao());

        return montarResposta(item);
    }

    /**
     * Exclusao logica. Apagando a linha de verdade, os registros do diario ficariam sem preco
     * e o custo que ja tinha sido consolidado mudaria para tras.
     */
    @Transactional
    public void desativar(Long id) {
        buscarDoUsuario(id).setAtivo(false);
    }

    /** Precos que ja valeram para esse item, do mais novo para o mais antigo (RF007). */
    @Transactional(readOnly = true)
    public List<HistoricoPrecoResponse> historico(Long id) {
        Long usuarioId = usuarioAutenticado.idDoUsuarioAutenticado();
        // Confere se o item e do usuario antes de ler o historico, senao o id de outra pessoa passaria.
        buscarDoUsuario(id);

        return historicoPrecoRepository
                .findByItemMercadoIdAndItemMercadoUsuarioIdOrderBySubstituidoEmDesc(id, usuarioId)
                .stream()
                .map(
                        historico ->
                                HistoricoPrecoResponse.de(
                                        historico,
                                        calculadora.custoUnitarioDe(
                                                historico.getPreco(),
                                                historico.getQuantidadeEmbalagem(),
                                                historico.getUnidade())))
                .toList();
    }

    /**
     * O custo unitario depende das tres informacoes juntas: mudar a embalagem de 1 L para 2 L muda
     * o custo por mL mesmo com o preco parado, e isso precisa entrar no historico.
     * Usa compareTo para 18.90 e 18.9 nao contarem como alteracao.
     */
    private boolean baseDePrecoMudou(ItemMercado item, ItemMercadoRequest requisicao) {
        return item.getPreco().compareTo(requisicao.preco()) != 0
                || item.getQuantidadeEmbalagem().compareTo(requisicao.quantidadeEmbalagem()) != 0
                || item.getUnidade() != requisicao.unidade();
    }

    private boolean estaEmUso(Long itemId, Long usuarioId) {
        return itemRefeicaoRepository.existsByItemMercadoIdAndItemMercadoUsuarioId(itemId, usuarioId)
                || itemRegistroRepository.existsByItemMercadoIdAndItemMercadoUsuarioId(
                        itemId, usuarioId);
    }

    private ItemMercado buscarDoUsuario(Long id) {
        return itemMercadoRepository
                .findByIdAndUsuarioId(id, usuarioAutenticado.idDoUsuarioAutenticado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item de mercado"));
    }

    private ItemMercadoResponse montarResposta(ItemMercado item) {
        return ItemMercadoResponse.de(item, calculadora.custoUnitario(item));
    }
}
