import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { iconeDaCategoria } from '../../core/dominio/categoria';
import {
  DataIso,
  DiaDoCalendario,
  dataCurta,
  dataDe,
  dataPorExtenso,
  diaDaSemanaCurto,
  diaDoMes,
  diaEMes,
  gradeDoMes,
  hoje,
  inicioDaSemana,
  inicioDoMes,
  mesPorExtenso,
  somarDias,
  somarMeses,
} from '../../core/dominio/data';
import { normalizar } from '../../core/dominio/texto';
import {
  UNIDADES,
  UnidadeMedida,
  grandezaDa,
  paraBase,
  rotuloDaUnidade,
} from '../../core/dominio/unidade';
import { positivo } from '../../core/dominio/validadores';
import { camposComErro, mensagemDeErro } from '../../core/http/erro-api';
import { ItemMercadoResponse } from '../mercado/item-mercado.model';
import { ItemMercadoService } from '../mercado/item-mercado.service';
import { BottomSheet } from '../../shared/bottom-sheet/bottom-sheet';
import { MoedaPipe } from '../../shared/moeda.pipe';
import { RefeicaoRequest, RefeicaoResponse } from './refeicao.model';
import { RefeicaoService } from './refeicao.service';
import { ItemRegistroResponse, RegistroDiarioResponse } from './registro-diario.model';
import { RegistroDiarioService } from './registro-diario.service';
import { Icone } from '../../shared/icone/icone';

type Aba = 'biblioteca' | 'diario';

/** Passo que esta aberto no painel de baixo. Quando e null, nao tem painel aberto. */
type Etapa =
  | 'formulario'
  | 'menu'
  | 'excluir'
  | 'escolher-item'
  | 'quantidade'
  | 'adicionar-ao-dia'
  | 'menu-registro'
  | 'registro'
  | 'remover-do-dia'
  | 'calendario';

/** Uma coluna da faixa de dias que fica no topo do diario. */
interface ColunaDeDia {
  readonly chave: DataIso;
  /** Fica "Seg", "Ter" e assim por diante. */
  readonly semana: string;
  readonly numero: number;
  /** Liga o pontinho verde, que indica que teve consumo registrado nesse dia. */
  readonly temRegistro: boolean;
}

/** Celula do calendario do mes, com o mesmo pontinho de consumo da faixa de dias. */
interface DiaDoCalendarioMarcado extends DiaDoCalendario {
  readonly temRegistro: boolean;
}

/**
 * Icones que aparecem no formulario da refeicao. Os nomes vem do mapa de shared/icone.
 *
 * A lista ficou menos variada depois da troca dos emojis pelo Solar, que nao tem ovo, sanduiche,
 * macarrao nem banana. Os que sobraram representam mais o momento da refeicao do que o prato.
 */
const ICONES = [
  'ref-manha',
  'ref-cafe',
  'ref-almoco',
  'ref-jantar',
  'ref-salada',
  'ref-arroz',
  'ref-massa',
  'ref-sanduiche',
  'ref-fruta',
  'ref-cereal',
  'ref-doce',
  'ref-bebida',
] as const;

/** Nome falado de cada icone. Vai para o aria-label do seletor. */
const ROTULO_DO_ICONE: Readonly<Record<(typeof ICONES)[number], string>> = {
  'ref-manha': 'Ovo',
  'ref-cafe': 'Café',
  'ref-almoco': 'Prato feito',
  'ref-jantar': 'Jantar',
  'ref-salada': 'Salada',
  'ref-arroz': 'Panela',
  'ref-massa': 'Massa',
  'ref-sanduiche': 'Sanduíche',
  'ref-fruta': 'Fruta',
  'ref-cereal': 'Sopa ou cereal',
  'ref-doce': 'Doce',
  'ref-bebida': 'Bebida',
};

const QUANTIDADE = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });

/** Item da refeicao enquanto ela esta sendo montada na tela, antes de ir para a API. */
interface ItemRascunho {
  readonly itemMercadoId: number;
  readonly nome: string;
  readonly quantidadeConsumida: number;
  readonly unidade: UnidadeMedida;
  /** Custo que a API devolveu para um item ja salvo. Vale ate a lista do mercado carregar. */
  readonly custoSalvo: number | null;
  /** False quer dizer item de mercado desativado. Ele continua vinculado, mas some da escolha. */
  readonly ativo: boolean;
}

/**
 * Serve as duas secoes do menu, decididas pelo campo aba: a biblioteca de refeicoes modelo
 * (RF003) e o diario de consumo (RF008). O custo de ambas vem calculado do backend (RF005).
 *
 * As duas nunca se misturam. Na biblioteca o que se edita e o modelo; no diario, o
 * RegistroDiario daquela data, que tem copia propria dos itens. Ajustar a quantidade de um
 * dia nao pode mexer no modelo, e so o diario entra no custo do periodo.
 */
@Component({
  selector: 'app-dieta',
  imports: [RouterLink, ReactiveFormsModule, BottomSheet, MoedaPipe, Icone],
  templateUrl: './dieta.html',
  styleUrl: './dieta.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dieta {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(RefeicaoService);
  private readonly mercado = inject(ItemMercadoService);
  private readonly diario = inject(RegistroDiarioService);
  private readonly injector = inject(Injector);

  protected readonly icones = ICONES;

  protected rotuloDoIcone(nome: (typeof ICONES)[number]): string {
    return ROTULO_DO_ICONE[nome];
  }

    /**
     * Nao e estado que muda: trocar de secao e navegar, e navegar cria uma instancia nova. Por isso
     * um campo comum e nao um signal.
     */
  protected readonly aba: Aba = inject(ActivatedRoute).snapshot.data['aba'] ?? 'biblioteca';

  protected readonly titulo = this.aba === 'biblioteca' ? 'Refeições' : 'Diário';

  protected readonly subtitulo =
    this.aba === 'biblioteca' ? 'Modelos salvos de refeições' : 'O que você comeu cada dia';

  protected readonly refeicoes = signal<RefeicaoResponse[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly busca = signal('');

    /**
     * So existe enquanto a tela esta aberta: o RefeicaoResponse nao tem campo de posicao e a API
     * devolve a biblioteca ordenada por titulo, entao nao ha onde salvar essa ordem.
     */
  private readonly ordem = signal<number[]>([]);

  protected readonly ordenadas = computed(() => {
    const posicao = new Map(this.ordem().map((id, indice) => [id, indice]));
    // O sort do JavaScript e estavel, entao id que nao esta na ordem local fica na posicao
    // em que o backend mandou.
    return [...this.refeicoes()].sort(
      (a, b) =>
        (posicao.get(a.id) ?? Number.MAX_SAFE_INTEGER) -
        (posicao.get(b.id) ?? Number.MAX_SAFE_INTEGER)
    );
  });

  protected readonly filtradas = computed(() => {
    const termo = normalizar(this.busca());
    if (!termo) {
      return this.ordenadas();
    }
    return this.ordenadas().filter((refeicao) => normalizar(refeicao.titulo).includes(termo));
  });

  protected readonly resumo = computed(() => {
    const total = this.refeicoes().length;
    return total === 1 ? '1 refeição salva' : `${total} refeições salvas`;
  });

  constructor() {
    // A biblioteca serve para as duas secoes. No Diario sao esses modelos que o usuario
    // registra no dia, e e deles que sai o aviso de "crie uma refeicao primeiro".
    this.carregar();

    // O custo do dia depende do preco de mercado de agora, que pode ter sido atualizado
    // (RF007) desde a ultima vez. Por isso a busca acontece toda vez que entra na tela.
    if (this.aba === 'diario') {
      this.carregarSemana();
    }
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.api.listar().subscribe({
      next: (refeicoes) => {
        this.refeicoes.set(refeicoes);
        this.reconciliarOrdem(refeicoes);
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregando.set(false);
        this.erro.set(mensagemDeErro(falha, 'Não foi possível carregar suas refeições.'));
      },
    });
  }

  /** Recarregar nao desfaz o que o usuario arrastou. So sai o que foi apagado e o que e novo
   *  entra no fim da lista. */
  private reconciliarOrdem(refeicoes: RefeicaoResponse[]): void {
    const existentes = new Set(refeicoes.map((refeicao) => refeicao.id));
    const mantida = this.ordem().filter((id) => existentes.has(id));
    const conhecidos = new Set(mantida);
    const novas = refeicoes.map((r) => r.id).filter((id) => !conhecidos.has(id));
    this.ordem.set([...mantida, ...novas]);
  }

  protected aoBuscar(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
  }

  protected limparBusca(): void {
    this.busca.set('');
  }

  protected readonly arrastando = signal<number | null>(null);

  /** Com a lista filtrada, arrastar jogaria a refeicao para uma posicao que ninguem esta vendo. */
  protected readonly podeReordenar = computed(
    () => !this.busca().trim() && this.filtradas().length > 1
  );

  protected iniciarArrasto(id: number, evento: DragEvent): void {
    this.arrastando.set(id);
    if (evento.dataTransfer) {
      evento.dataTransfer.effectAllowed = 'move';
      // O Firefox so dispara o dragover se o arrasto estiver carregando algum dado.
      evento.dataTransfer.setData('text/plain', String(id));
    }
  }

  protected sobrepor(id: number, evento: DragEvent): void {
    evento.preventDefault();
    const origem = this.arrastando();
    if (origem !== null && origem !== id) {
      this.reposicionar(origem, this.ordenadas().findIndex((refeicao) => refeicao.id === id));
    }
  }

  protected encerrarArrasto(): void {
    this.arrastando.set(null);
  }

  protected moverPorTeclado(id: number, passo: -1 | 1, evento: Event): void {
    evento.preventDefault();
    const atual = this.ordenadas().findIndex((refeicao) => refeicao.id === id);
    this.reposicionar(id, atual + passo);

    // Mudar o elemento de lugar no DOM joga o foco para o body. Sem devolver o foco para a
    // alca, cada seta andaria so uma posicao e a proxima tecla se perderia.
    const alca = evento.target as HTMLElement;
    afterNextRender(() => alca.focus(), { injector: this.injector });
  }

  private reposicionar(id: number, destino: number): void {
    const ids = this.ordenadas().map((refeicao) => refeicao.id);
    const origem = ids.indexOf(id);
    if (origem < 0 || destino < 0 || destino >= ids.length) {
      return;
    }
    ids.splice(destino, 0, ...ids.splice(origem, 1));
    this.ordem.set(ids);
  }

  protected readonly etapa = signal<Etapa | null>(null);
  protected readonly emEdicao = signal<RefeicaoResponse | null>(null);
  protected readonly alvoMenu = signal<RefeicaoResponse | null>(null);
  protected readonly alvoItem = signal<ItemMercadoResponse | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroForm = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});

  protected readonly tituloSheet = computed(() => {
    switch (this.etapa()) {
      case 'formulario':
        return this.emEdicao() ? 'Editar refeição' : 'Nova refeição';
      case 'menu': {
        const alvo = this.alvoMenu();
        return alvo ? alvo.titulo : 'Refeição';
      }
      case 'excluir':
        return 'Excluir refeição';
      case 'escolher-item':
        return 'Escolher item';
      case 'quantidade':
        return this.alvoItem()?.nome ?? 'Quantidade';
      case 'adicionar-ao-dia':
        return 'Adicionar ao dia';
      case 'menu-registro': {
        const alvo = this.alvoRegistro();
        return alvo ? alvo.titulo : 'Refeição do dia';
      }
      case 'registro': {
        const alvo = this.alvoRegistro();
        return alvo ? alvo.titulo : 'Refeição do dia';
      }
      case 'remover-do-dia':
        return 'Remover do dia';
      case 'calendario':
        return 'Escolher data';
      default:
        return '';
    }
  });

  /** No fluxo de montar a refeicao, o botao de fechar e o veu voltam um passo. Nos outros, fecham. */
  protected aoFechar(): void {
    const etapa = this.etapa();
    if (etapa === 'escolher-item' || etapa === 'quantidade') {
      this.etapa.set('formulario');
      return;
    }
    this.fecharSheet();
  }

  protected fecharSheet(): void {
    this.etapa.set(null);
    this.emEdicao.set(null);
    this.alvoMenu.set(null);
    this.alvoItem.set(null);
    this.alvoRegistro.set(null);
  }

  /** O botao de mais cria um modelo na aba Refeicoes e registra consumo na aba Diario. */
  protected aoTocarNoFab(): void {
    if (this.aba === 'biblioteca') {
      this.abrirNovo();
    } else {
      this.abrirAdicionarAoDia();
    }
  }

  protected readonly form = this.fb.group({
    titulo: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(120)]),
  });

  protected readonly icone = signal<string>(ICONES[2]);
  private readonly rascunho = signal<ItemRascunho[]>([]);

  protected escolherIcone(icone: string): void {
    this.icone.set(icone);
  }

  protected abrirNovo(): void {
    this.prepararFormulario(null);
    this.form.reset({ titulo: '' });
    this.icone.set(ICONES[1]);
    this.rascunho.set([]);
  }

  protected abrirEdicao(refeicao: RefeicaoResponse): void {
    this.prepararFormulario(refeicao);
    this.form.reset({ titulo: refeicao.titulo });
    this.icone.set(refeicao.icone ?? ICONES[1]);
    this.rascunho.set(
      refeicao.itens.map((item) => ({
        itemMercadoId: item.itemMercadoId,
        nome: item.nome,
        quantidadeConsumida: item.quantidadeConsumida,
        unidade: item.unidade,
        custoSalvo: item.custo,
        ativo: item.itemAtivo,
      }))
    );
  }

  private prepararFormulario(refeicao: RefeicaoResponse | null): void {
    this.emEdicao.set(refeicao);
    this.alvoMenu.set(null);
    this.erroForm.set(null);
    this.errosServidor.set({});
    this.salvando.set(false);
    this.etapa.set('formulario');
    this.carregarMercado();
  }

  protected readonly itensMercado = signal<ItemMercadoResponse[]>([]);
  protected readonly carregandoMercado = signal(false);
  protected readonly erroMercado = signal<string | null>(null);

  /** Busca de novo toda vez que abre, porque o preco da embalagem pode ter mudado. */
  protected carregarMercado(): void {
    this.carregandoMercado.set(true);
    this.erroMercado.set(null);

    this.mercado.listar().subscribe({
      next: (itens) => {
        this.itensMercado.set(itens);
        this.carregandoMercado.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregandoMercado.set(false);
        this.erroMercado.set(mensagemDeErro(falha, 'Não foi possível carregar os itens.'));
      },
    });
  }

    /**
     * Previa recalculada pelo preco de hoje; quem grava o valor de verdade e o backend. O custo fica
     * null quando nao da para calcular, nunca R$ 0,00.
     */
  protected readonly composicao = computed(() =>
    this.rascunho().map((item) => ({ ...item, custo: this.custoDe(item) }))
  );

  private custoDe(item: ItemRascunho): number | null {
    const mercado = this.itensMercado().find((candidato) => candidato.id === item.itemMercadoId);
    if (!mercado) {
      // Item que nao esta na lista, ou porque foi desativado ou porque o mercado ainda esta
      // carregando. Nesse caso vale o custo que a API mandou.
      return item.custoSalvo;
    }
    if (
      item.quantidadeConsumida <= 0 ||
      grandezaDa(mercado.unidade) !== grandezaDa(item.unidade)
    ) {
      return null;
    }
    return mercado.custoUnitario * paraBase(item.quantidadeConsumida, item.unidade);
  }

  /** Soma dos itens que da para calcular. Item sem custo fica de fora e e avisado na tela. */
  protected readonly custoRascunho = computed(() =>
    this.composicao().reduce((soma, item) => soma + (item.custo ?? 0), 0)
  );

  protected readonly temItemSemCusto = computed(() =>
    this.composicao().some((item) => item.custo === null)
  );

  protected removerItem(indice: number): void {
    this.rascunho.update((itens) => itens.filter((_, posicao) => posicao !== indice));
  }

  protected readonly buscaItem = signal('');

    /**
     * Filtra a lista que ja esta na memoria e nunca com chamada nova, porque e dessa mesma lista que
     * o custoDe tira o custo de cada item.
     */
  protected readonly itensMercadoFiltrados = computed(() => {
    const termo = normalizar(this.buscaItem());
    if (!termo) {
      return this.itensMercado();
    }
    return this.itensMercado().filter((item) => normalizar(item.nome).includes(termo));
  });

  protected aoBuscarItem(evento: Event): void {
    this.buscaItem.set((evento.target as HTMLInputElement).value);
  }

  protected limparBuscaItem(): void {
    this.buscaItem.set('');
  }

  /** A lista do mercado ja foi buscada quando o formulario abriu, aqui so mostro o resultado. */
  protected abrirEscolhaDeItem(): void {
    this.erroForm.set(null);
    // Se abrisse com o termo da vez anterior, o catalogo apareceria filtrado sem o usuario
    // entender o motivo.
    this.buscaItem.set('');
    this.etapa.set('escolher-item');
  }

  protected readonly formItem = this.fb.group({
    quantidadeConsumida: this.fb.control<number | null>(null, [Validators.required, positivo]),
    unidade: this.fb.nonNullable.control<UnidadeMedida>('G', Validators.required),
  });

  private readonly valoresDoItem = toSignal(this.formItem.valueChanges, {
    initialValue: this.formItem.getRawValue(),
  });

  /** So mostra unidades da mesma grandeza da embalagem. Consumir em g um item vendido em L
   *  seria entrada invalida. */
  protected readonly unidadesDoAlvo = computed(() => {
    const alvo = this.alvoItem();
    if (!alvo) {
      return [];
    }
    const grandeza = grandezaDa(alvo.unidade);
    return UNIDADES.filter((unidade) => unidade.grandeza === grandeza);
  });

  protected escolherItem(item: ItemMercadoResponse): void {
    this.alvoItem.set(item);

    // Abre ja na unidade base e com uma quantidade que faz sentido: 100 g, 100 mL ou 1 un.
    const grandeza = grandezaDa(item.unidade);
    const unidade: UnidadeMedida = grandeza === 'MASSA' ? 'G' : grandeza === 'VOLUME' ? 'ML' : 'UN';
    this.formItem.reset({ quantidadeConsumida: grandeza === 'CONTAGEM' ? 1 : 100, unidade });

    this.etapa.set('quantidade');
  }

  /** Mostra o custo da porcao enquanto o usuario digita a quantidade. */
  protected readonly previaItem = computed(() => {
    const alvo = this.alvoItem();
    const { quantidadeConsumida, unidade } = this.valoresDoItem();
    if (!alvo || !quantidadeConsumida || quantidadeConsumida <= 0) {
      return null;
    }
    return alvo.custoUnitario * paraBase(quantidadeConsumida, unidade ?? 'G');
  });

  protected adicionarItem(): void {
    const alvo = this.alvoItem();
    if (!alvo || this.formItem.invalid) {
      this.formItem.markAllAsTouched();
      return;
    }

    const { quantidadeConsumida, unidade } = this.formItem.getRawValue();
    this.rascunho.update((itens) => [
      ...itens,
      {
        itemMercadoId: alvo.id,
        nome: alvo.nome,
        quantidadeConsumida: quantidadeConsumida!,
        unidade,
        custoSalvo: null,
        ativo: true,
      },
    ]);

    this.alvoItem.set(null);
    this.etapa.set('formulario');
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.rascunho().length) {
      this.erroForm.set('Adicione ao menos um item do mercado à refeição.');
      return;
    }

    const requisicao: RefeicaoRequest = {
      titulo: this.form.getRawValue().titulo.trim(),
      icone: this.icone(),
      itens: this.rascunho().map((item) => ({
        itemMercadoId: item.itemMercadoId,
        quantidadeConsumida: item.quantidadeConsumida,
        unidade: item.unidade,
      })),
    };

    this.salvando.set(true);
    this.erroForm.set(null);
    this.errosServidor.set({});

    const refeicao = this.emEdicao();
    const gravacao = refeicao
      ? this.api.atualizar(refeicao.id, requisicao)
      : this.api.criar(requisicao);

    gravacao.subscribe({
      next: () => {
        this.fecharSheet();
        // Recarrega em vez de corrigir na mao, porque o custo total vem recalculado do servidor.
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.salvando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível salvar a refeição.'));
      },
    });
  }

  protected abrirMenu(refeicao: RefeicaoResponse): void {
    this.alvoMenu.set(refeicao);
    this.erroForm.set(null);
    this.excluindo.set(false);
    this.etapa.set('menu');
  }

  protected editarDoMenu(): void {
    const alvo = this.alvoMenu();
    if (alvo) {
      this.abrirEdicao(alvo);
    }
  }

  protected confirmarExclusao(): void {
    this.etapa.set('excluir');
  }

  protected excluir(): void {
    const alvo = this.alvoMenu();
    if (!alvo) {
      return;
    }

    this.excluindo.set(true);
    this.erroForm.set(null);

    this.api.excluir(alvo.id).subscribe({
      next: () => {
        this.fecharSheet();
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.excluindo.set(false);
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível excluir a refeição.'));
      },
    });
  }

  protected readonly diaSelecionado = signal<DataIso>(hoje());
  protected readonly inicioSemana = signal<DataIso>(inicioDaSemana(hoje()));

  /**
   * Registros do intervalo que foi carregado, que e a semana que aparece na tela mais o dia
   * anterior a ela. Esse dia extra nao aparece na faixa, serve so para saber se o
   * botao de repetir o dia anterior tem de onde copiar quando a segunda esta selecionada.
   */
  private readonly registros = signal<RegistroDiarioResponse[]>([]);
  protected readonly carregandoDiario = signal(true);
  protected readonly erroDiario = signal<string | null>(null);

  /** Erro de uma acao especifica, como registrar, repetir ou remover, e nao da carga da tela. */
  protected readonly erroAcao = signal<string | null>(null);
  protected readonly registrando = signal(false);
  protected readonly repetindo = signal(false);
  protected readonly removendo = signal(false);

  protected readonly fimSemana = computed(() => somarDias(this.inicioSemana(), 6));

  /** Monta o texto "01 ago - 07 ago de 2026". */
  protected readonly intervaloDaSemana = computed(() => {
    const fim = this.fimSemana();
    return `${diaEMes(this.inicioSemana())} – ${diaEMes(fim)} de ${dataDe(fim).getFullYear()}`;
  });

  private readonly diasComRegistro = computed(
    () => new Set(this.registros().map((registro) => registro.data))
  );

  protected readonly dias = computed<ColunaDeDia[]>(() => {
    const marcados = this.diasComRegistro();
    return Array.from({ length: 7 }, (_, deslocamento) => {
      const chave = somarDias(this.inicioSemana(), deslocamento);
      return {
        chave,
        semana: diaDaSemanaCurto(chave),
        numero: diaDoMes(chave),
        temRegistro: marcados.has(chave),
      };
    });
  });

  protected readonly registrosDoDia = computed(() =>
    this.registros().filter((registro) => registro.data === this.diaSelecionado())
  );

  /** Soma dos registros do dia. Cada custoTotal ja vem sem os itens que estao sem preco. */
  protected readonly custoDoDia = computed(() =>
    this.registrosDoDia().reduce((soma, registro) => soma + registro.custoTotal, 0)
  );

  /** Itens que ficaram de fora do total do dia. Sem esse aviso o valor engana o usuario. */
  protected readonly semPrecoNoDia = computed(() => [
    ...new Set(this.registrosDoDia().flatMap((registro) => registro.itensSemPreco)),
  ]);

  protected readonly diaAnterior = computed(() => somarDias(this.diaSelecionado(), -1));

  /**
   * So mostro o botao de repetir quando o dia anterior tem mesmo o que copiar. A API responde
   * 400 se ele estiver vazio, e um botao que so sabe dar erro nao ajuda ninguem.
   */
  protected readonly podeRepetirDiaAnterior = computed(() =>
    this.diasComRegistro().has(this.diaAnterior())
  );

  protected carregarSemana(): void {
    this.carregandoDiario.set(true);
    this.erroDiario.set(null);
    this.erroAcao.set(null);

    // Uma requisicao so ja cobre a faixa de dias e a lista do dia selecionado. O pontinho de
    // cada coluna sai da mesma resposta, entao nao preciso fazer sete chamadas.
    this.diario.listarPorIntervalo(somarDias(this.inicioSemana(), -1), this.fimSemana()).subscribe({
      next: (registros) => {
        this.registros.set(registros);
        this.carregandoDiario.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregandoDiario.set(false);
        this.erroDiario.set(mensagemDeErro(falha, 'Não foi possível carregar o diário.'));
      },
    });
  }

  protected selecionarDia(chave: DataIso): void {
    this.diaSelecionado.set(chave);
    this.erroAcao.set(null);
  }

    /**
     * Leva a selecao junto, para o mesmo dia da semana. Com o dia parado, como no prototipo, a lista
     * de baixo descolaria da faixa de cima: nenhuma coluna marcada e refeicoes de outra semana.
     */
  protected mudarSemana(passo: -1 | 1): void {
    this.inicioSemana.update((inicio) => somarDias(inicio, passo * 7));
    this.diaSelecionado.update((dia) => somarDias(dia, passo * 7));
    this.carregarSemana();
  }

  protected readonly diaDeHoje: DataIso = hoje();

  /**
   * Mes aberto na grade do calendario.
   *
   * A faixa de dias so mostra uma semana por vez. O calendario existe para pular direto para
   * uma data distante, tipo o mes passado, sem ficar clicando na seta varias vezes.
   */
  private readonly mesCalendario = signal<DataIso>(inicioDoMes(hoje()));

  protected readonly mesCalendarioRotulo = computed(() => mesPorExtenso(this.mesCalendario()));

    /**
     * Lista separada de registros porque aquela cobre so a semana visivel e o calendario mostra seis.
     * Reaproveitando, os dias que o usuario abriu o calendario para procurar e que ficariam sem o
     * pontinho.
     */
  private readonly diasComRegistroNoMes = signal<ReadonlySet<DataIso>>(new Set());

  protected readonly diasCalendario = computed<DiaDoCalendarioMarcado[]>(() => {
    const marcados = this.diasComRegistroNoMes();
    return gradeDoMes(this.mesCalendario()).map((dia) => ({
      ...dia,
      temRegistro: marcados.has(dia.chave),
    }));
  });

    /**
     * Cobre tambem as celulas dos meses vizinhos, que sao clicaveis e por isso precisam do pontinho.
     *
     * Erro e ignorado de proposito: o pontinho e detalhe visual e nao deve travar a navegacao.
     */
  private carregarMarcadoresDoMes(): void {
    const grade = gradeDoMes(this.mesCalendario());
    const inicio = grade[0].chave;
    const fim = grade[grade.length - 1].chave;

    this.diario.listarPorIntervalo(inicio, fim).subscribe({
      next: (registros) =>
        this.diasComRegistroNoMes.set(new Set(registros.map((registro) => registro.data))),
      error: () => this.diasComRegistroNoMes.set(new Set()),
    });
  }

  /** Abre sempre no mes do dia que esta selecionado, e nao no mes de hoje. */
  protected abrirCalendario(): void {
    this.mesCalendario.set(inicioDoMes(this.diaSelecionado()));
    this.diasComRegistroNoMes.set(new Set());
    this.erroAcao.set(null);
    this.etapa.set('calendario');
    this.carregarMarcadoresDoMes();
  }

  protected mudarMesCalendario(passo: -1 | 1): void {
    this.mesCalendario.update((mes) => somarMeses(mes, passo));
    this.carregarMarcadoresDoMes();
  }

  /** Aceita tambem as celulas de fora do mes: clicar em 31/jul na grade de agosto vai para julho. */
  protected selecionarDataNoCalendario(chave: DataIso): void {
    this.inicioSemana.set(inicioDaSemana(chave));
    this.diaSelecionado.set(chave);
    this.erroAcao.set(null);
    this.carregarSemana();
    this.fecharSheet();
  }

  protected irParaHojeNoCalendario(): void {
    this.selecionarDataNoCalendario(this.diaDeHoje);
  }

  /** Mesma ideia do rotulo da faixa. O pontinho verde e aria-hidden, entao quem usa leitor de
   *  tela precisa ouvir aqui se o dia tem consumo. */
  protected rotuloDataCalendario(chave: DataIso, temRegistro: boolean): string {
    return `${dataPorExtenso(chave)} — ${
      temRegistro ? 'com refeições registradas' : 'sem registro'
    }`;
  }

  protected readonly buscaRefeicaoDia = signal('');

    /**
     * Signal proprio, separado do busca da aba Refeicoes: sao dois campos em telas diferentes e,
     * compartilhando o termo, um filtraria o outro sem o usuario perceber.
     */
  protected readonly refeicoesFiltradasDoDia = computed(() => {
    const termo = normalizar(this.buscaRefeicaoDia());
    if (!termo) {
      return this.ordenadas();
    }
    return this.ordenadas().filter((refeicao) => normalizar(refeicao.titulo).includes(termo));
  });

  protected aoBuscarRefeicaoDia(evento: Event): void {
    this.buscaRefeicaoDia.set((evento.target as HTMLInputElement).value);
  }

  protected limparBuscaRefeicaoDia(): void {
    this.buscaRefeicaoDia.set('');
  }

  protected abrirAdicionarAoDia(): void {
    this.erroAcao.set(null);
    this.registrando.set(false);
    // Se abrisse com o termo anterior, alguns modelos ficariam escondidos sem explicacao.
    this.buscaRefeicaoDia.set('');
    this.etapa.set('adicionar-ao-dia');
  }

  protected registrarNoDia(refeicao: RefeicaoResponse): void {
    this.registrando.set(true);
    this.erroAcao.set(null);

    this.diario
      .registrar({ data: this.diaSelecionado(), refeicaoId: refeicao.id })
      .subscribe({
        next: (registro) => {
          this.registrando.set(false);
          // Ja vem pronto do servidor, com a copia dos itens e o custo calculado.
          this.registros.update((atuais) => [...atuais, registro]);
          this.fecharSheet();
        },
        error: (falha: HttpErrorResponse) => {
          this.registrando.set(false);
          this.erroAcao.set(mensagemDeErro(falha, 'Não foi possível registrar a refeição.'));
        },
      });
  }

  protected repetirDiaAnterior(): void {
    this.repetindo.set(true);
    this.erroAcao.set(null);

    this.diario.duplicarDiaAnterior(this.diaSelecionado()).subscribe({
      next: (criados) => {
        this.repetindo.set(false);
        this.registros.update((atuais) => [...atuais, ...criados]);
      },
      error: (falha: HttpErrorResponse) => {
        this.repetindo.set(false);
        this.erroAcao.set(mensagemDeErro(falha, 'Não foi possível repetir o dia anterior.'));
      },
    });
  }

  protected readonly alvoRegistro = signal<RegistroDiarioResponse | null>(null);
  /** Id do item que esta sendo salvo agora. Serve para travar so aquela linha. */
  protected readonly ajustandoItem = signal<number | null>(null);
  protected readonly erroAjuste = signal<string | null>(null);

    /**
     * As duas acoes fazem coisas bem diferentes — ajustar a quantidade do dia e tirar a refeicao do
     * dia. Por isso um menu, e nao um botao de fechar solto que apagaria tudo no primeiro toque.
     */
  protected abrirMenuDoRegistro(registro: RegistroDiarioResponse): void {
    this.alvoRegistro.set(registro);
    this.erroAcao.set(null);
    this.etapa.set('menu-registro');
  }

  protected editarRegistroDoMenu(): void {
    const alvo = this.alvoRegistro();
    if (alvo) {
      this.abrirRegistro(alvo);
    }
  }

  /** Passa pela confirmacao em vez de apagar direto: remover um dia nao tem desfazer. */
  protected removerRegistroDoMenu(): void {
    const alvo = this.alvoRegistro();
    if (alvo) {
      this.confirmarRemocaoDoDia(alvo);
    }
  }

  protected abrirRegistro(registro: RegistroDiarioResponse): void {
    this.alvoRegistro.set(registro);
    this.erroAjuste.set(null);
    this.ajustandoItem.set(null);
    this.etapa.set('registro');
  }

    /**
     * O PATCH mexe em uma linha de ItemRegistro, entao o modelo e os outros dias continuam iguais e a
     * biblioteca em memoria nem precisa ser recarregada.
     */
  protected ajustarQuantidade(item: ItemRegistroResponse, evento: Event): void {
    const registro = this.alvoRegistro();
    const campo = evento.target as HTMLInputElement;
    const quantidade = campo.valueAsNumber;

    if (!registro) {
      return;
    }

    // Valido aqui, antes de salvar, e nao na hora de calcular o custo.
    if (!Number.isFinite(quantidade) || quantidade <= 0) {
      this.erroAjuste.set('A quantidade deve ser maior que zero.');
      campo.value = String(item.quantidadeConsumida);
      return;
    }
    if (quantidade === item.quantidadeConsumida) {
      return;
    }

    this.ajustandoItem.set(item.id);
    this.erroAjuste.set(null);

    this.diario
      .ajustarItem(registro.id, item.id, {
        quantidadeConsumida: quantidade,
        unidade: item.unidade,
      })
      .subscribe({
        next: (atualizado) => {
          this.ajustandoItem.set(null);
          this.substituirRegistro(atualizado);
          this.alvoRegistro.set(atualizado);
        },
        error: (falha: HttpErrorResponse) => {
          this.ajustandoItem.set(null);
          campo.value = String(item.quantidadeConsumida);
          this.erroAjuste.set(mensagemDeErro(falha, 'Não foi possível ajustar a quantidade.'));
        },
      });
  }

  private substituirRegistro(atualizado: RegistroDiarioResponse): void {
    this.registros.update((atuais) =>
      atuais.map((registro) => (registro.id === atualizado.id ? atualizado : registro))
    );
  }

  protected confirmarRemocaoDoDia(registro: RegistroDiarioResponse): void {
    this.alvoRegistro.set(registro);
    this.erroAcao.set(null);
    this.removendo.set(false);
    this.etapa.set('remover-do-dia');
  }

  protected removerDoDia(): void {
    const alvo = this.alvoRegistro();
    if (!alvo) {
      return;
    }

    this.removendo.set(true);
    this.erroAcao.set(null);

    this.diario.remover(alvo.id).subscribe({
      next: () => {
        this.registros.update((atuais) =>
          atuais.filter((registro) => registro.id !== alvo.id)
        );
        this.fecharSheet();
      },
      error: (falha: HttpErrorResponse) => {
        this.removendo.set(false);
        this.erroAcao.set(mensagemDeErro(falha, 'Não foi possível remover a refeição do dia.'));
      },
    });
  }

  /** Monta "03/08/2026", para a data aparecer dentro de uma frase. */
  protected readonly diaCurto = computed(() => dataCurta(this.diaSelecionado()));

  protected readonly diaAnteriorCurto = computed(() => dataCurta(this.diaAnterior()));

  /** Texto da coluna para o leitor de tela: a data por extenso mais o que tem naquele dia. */
  protected rotuloDoDia(dia: ColunaDeDia): string {
    return `${dataPorExtenso(dia.chave)} — ${
      dia.temRegistro ? 'com refeições registradas' : 'sem registro'
    }`;
  }

  protected invalidoTitulo(): boolean {
    const controle = this.form.controls.titulo;
    return (controle.touched && controle.invalid) || 'titulo' in this.errosServidor();
  }

  protected mensagemTitulo(): string | null {
    const doServidor = this.errosServidor()['titulo'];
    if (doServidor) {
      return doServidor;
    }

    const controle = this.form.controls.titulo;
    if (!controle.touched || controle.valid) {
      return null;
    }
    return controle.hasError('maxlength')
      ? 'O título deve ter no máximo 120 caracteres.'
      : 'Dê um título à refeição.';
  }

  protected mensagemQuantidade(): string | null {
    const controle = this.formItem.controls.quantidadeConsumida;
    if (!controle.touched || controle.valid) {
      return null;
    }
    return controle.hasError('positivo')
      ? 'A quantidade deve ser maior que zero.'
      : 'Informe a quantidade consumida.';
  }

  /** Monta "4 itens" ou "1 item". */
  protected contagem(total: number): string {
    return total === 1 ? '1 item' : `${total} itens`;
  }

  /** Monta "150 g", "0,5 un". */
  protected medida(quantidade: number, unidade: UnidadeMedida): string {
    return `${QUANTIDADE.format(quantidade)} ${rotuloDaUnidade(unidade)}`;
  }

  /** So o sufixo, porque no diario a quantidade fica em um campo editavel do lado. */
  protected unidadeDe(unidade: UnidadeMedida): string {
    return rotuloDaUnidade(unidade);
  }

  protected iconeDoItem(item: ItemMercadoResponse): string {
    return iconeDaCategoria(item.categoria);
  }
}
