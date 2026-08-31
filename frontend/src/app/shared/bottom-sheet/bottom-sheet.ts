import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Icone } from '../icone/icone';

/** Seletor do que o navegador considera focavel e ainda esta habilitado. */
const FOCAVEIS =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]),' +
  ' textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Painel que sobe de baixo da tela, o mesmo .sheet do prototipo.
 *
 * Quem usa controla a abertura com @if, e o componente cuida de fechar (botao, veu, Esc e arrastar
 * a alca). O corpo vem por ng-content.
 *
 * Como e modal, tambem cuida do foco: joga para dentro ao abrir, prende o Tab e devolve ao fechar.
 * Sem isso o Tab sai passeando pelo conteudo atras do veu, que esta bloqueado visualmente mas
 * continua alcancavel pelo teclado.
 */
@Component({
  selector: 'app-bottom-sheet',
  imports: [Icone],
  templateUrl: './bottom-sheet.html',
  styleUrl: './bottom-sheet.scss',
  host: { '(document:keydown)': 'aoTeclar($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomSheet implements OnDestroy {
  readonly titulo = input.required<string>();
  readonly fechar = output<void>();

  private readonly painel = viewChild.required<ElementRef<HTMLElement>>('painel');
  private readonly corpo = viewChild.required<ElementRef<HTMLElement>>('corpo');

    /**
     * Curto demais fecha em qualquer encostada; longo demais obriga a arrastar o painel inteiro. Esse
     * o dedo percorre sem esforco, mas dificilmente sem querer.
     */
  private static readonly LIMIAR_PARA_FECHAR = 90;

    /**
     * Quem fecha o painel normalmente e o transitionend. Sem esse limite, a aba em segundo plano ou a
     * transicao interrompida deixariam o painel preso na tela para sempre.
     */
  private static readonly ESPERA_MAXIMA_DE_SAIDA = 400;

  protected readonly arrasto = signal(0);
  protected readonly arrastando = signal(false);

  /** Fica true enquanto o painel esta terminando de descer, antes do pai remover ele. */
  protected readonly saindo = signal(false);

  private inicioY = 0;
  private temporizadorDeSaida?: ReturnType<typeof setTimeout>;

  protected readonly deslocamento = computed(() => {
    // Empurra para fora da tela o resto do caminho que o dedo nao percorreu.
    if (this.saindo()) {
      return 'translateY(100%)';
    }
    return this.arrasto() > 0 ? `translateY(${this.arrasto()}px)` : null;
  });

  protected aoPressionar(evento: PointerEvent): void {
    // O botao de fechar fica dentro da area de arraste, entao apertar nele e clique e nao gesto.
    if ((evento.target as HTMLElement).closest('button')) {
      return;
    }
    // Se ja esta saindo, um toque novo nao pode reabrir o gesto no meio da animacao.
    if (this.saindo()) {
      return;
    }

    this.inicioY = evento.clientY;
    this.arrastando.set(true);
    // Capturo o ponteiro para o gesto continuar chegando aqui mesmo se o dedo sair da alca.
    // Como o painel desce junto com o dedo, isso acontece quase na hora.
    (evento.currentTarget as HTMLElement).setPointerCapture(evento.pointerId);
  }

  protected aoMover(evento: PointerEvent): void {
    if (!this.arrastando()) {
      return;
    }
    // So aceita movimento para baixo. Para cima o painel passaria do topo da tela.
    this.arrasto.set(Math.max(0, evento.clientY - this.inicioY));
  }

  protected aoSoltar(): void {
    if (!this.arrastando()) {
      return;
    }

    const percorrido = this.arrasto();
    this.arrastando.set(false);

    if (percorrido < BottomSheet.LIMIAR_PARA_FECHAR) {
      // Volta para o lugar. Quem faz o movimento e a transicao do CSS.
      this.arrasto.set(0);
      return;
    }

    // Quem configurou o sistema para nao ter animacao tambem nao ve essa. O painel sai na hora.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      this.fechar.emit();
      return;
    }

    // Se o dedo soltar no meio, o painel termina de descer antes de sumir. Emitindo o
    // evento agora, o pai tiraria ele do DOM na hora e o painel sumiria no ar.
    this.saindo.set(true);
    this.temporizadorDeSaida = setTimeout(
        () => this.fechar.emit(), BottomSheet.ESPERA_MAXIMA_DE_SAIDA);
  }

  /** Terminou a descida, agora sim o painel pode ser removido. */
  protected aoTerminarTransicao(evento: TransitionEvent): void {
    // Confere o target porque um elemento filho com transicao de transform tambem dispara
    // esse evento aqui, e fecharia o painel no meio da animacao de outra coisa.
    if (
      this.saindo()
      && evento.propertyName === 'transform'
      && evento.target === this.painel().nativeElement
    ) {
      clearTimeout(this.temporizadorDeSaida);
      this.fechar.emit();
    }
  }

    /**
     * Lido no construtor de proposito: o clique que abriu o painel ja aconteceu, mas o HTML dele
     * ainda nao entrou na tela.
     */
  private readonly origemDoFoco = document.activeElement as HTMLElement | null;

  constructor() {
    // Titulo novo quer dizer conteudo novo. Em um painel de varios passos, o passo seguinte
    // abriria na mesma altura de rolagem do anterior, quase sempre no meio da lista, e com o
    // foco preso em um botao que nem existe mais. Quem recebe o foco e o painel, e nao o
    // primeiro campo, para o leitor de tela anunciar o titulo antes do usuario comecar a
    // preencher.
    effect(() => {
      this.titulo();
      this.corpo().nativeElement.scrollTop = 0;
      this.painel().nativeElement.focus({ preventScroll: true });
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorDeSaida);

    // Sem devolver o foco, ele cairia no body e a proxima tecla recomecaria do topo
    // da pagina. O isConnected cobre o caso do botao que abriu o painel ter sumido junto com
    // a acao que terminou.
    if (this.origemDoFoco?.isConnected) {
      this.origemDoFoco.focus({ preventScroll: true });
    }
  }

  protected aoTeclar(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      this.fechar.emit();
      return;
    }
    if (evento.key === 'Tab') {
      this.prenderTab(evento);
    }
  }

  /** Clique no veu fecha o painel. Clique dentro dele nao pode subir e fechar junto. */
  protected aoClicarNoVeu(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  /** Faz o Tab dar a volta dentro do painel em vez de escapar para o conteudo do fundo. */
  private prenderTab(evento: KeyboardEvent): void {
    const painel = this.painel().nativeElement;
    const focaveis = Array.from(painel.querySelectorAll<HTMLElement>(FOCAVEIS)).filter(
      // offsetParent nulo indica elemento escondido, como o passo anterior de um painel de
      // varias etapas.
      (elemento) => elemento.offsetParent !== null
    );

    if (!focaveis.length) {
      evento.preventDefault();
      painel.focus({ preventScroll: true });
      return;
    }

    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    const ativo = document.activeElement;
    // Foco fora do painel: ou e o proprio painel, que nao entra na lista, ou o elemento que
    // tinha o foco foi removido e o foco caiu no body.
    const fora = !painel.contains(ativo);

    if (evento.shiftKey && (fora || ativo === primeiro)) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && (fora || ativo === ultimo)) {
      evento.preventDefault();
      primeiro.focus();
    }
  }
}
