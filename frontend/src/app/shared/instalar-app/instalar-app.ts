import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  PLATFORM_ID,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { Icone } from '../icone/icone';

/**
 * Evento que o Chrome dispara quando a aplicacao pode ser instalada. O tipo e declarado
 * na mao porque ele nao existe na lib padrao do TypeScript, so em navegadores Chromium.
 */
interface EventoDeInstalacao extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * Prefixo da chave que grava a recusa. A chave completa leva o id do usuario. Isso e
 * preferencia de tela de uma pessoa e nao dado do sistema, por isso fica aqui e nao na API.
 */
const PREFIXO_DISPENSA = 'mealmath:instalar-dispensado';

/**
 * Faixa que convida o usuario a instalar o app na tela de inicio do celular.
 *
 * Manifest e service worker deixam a aplicacao instalavel, mas nao convidam ninguem. O Chrome
 * dispara beforeinstallprompt e espera a aplicacao oferecer, senao o usuario teria que achar
 * "Instalar aplicativo" no menu do navegador. Ja o Safari do iPhone nao dispara evento nem tem API
 * de instalacao, entao ali so da para explicar o caminho.
 */
@Component({
  selector: 'app-instalar-app',
  imports: [Icone],
  templateUrl: './instalar-app.html',
  styleUrl: './instalar-app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstalarApp {
  private readonly navegador = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly auth = inject(AuthService);

  private readonly evento = signal<EventoDeInstalacao | null>(null);

  /**
   * Diz se ja esta instalado ou ja rodando como app. Isso e situacao do aparelho e nao da
   * conta, entao nao tem o que oferecer para ninguem que entre por aqui.
   */
  private readonly jaEhApp = signal(false);

  protected readonly instalando = signal(false);

  /** O iPhone nao tem API de instalacao, entao em vez de botao a faixa mostra o passo a passo. */
  protected readonly ehIos = signal(false);

    /**
     * A recusa e por conta e nao por navegador, senao quem entrasse depois no mesmo aparelho nunca
     * veria o convite. O linkedSignal rele o armazenamento quando o usuario da sessao muda.
     */
  private readonly dispensado = linkedSignal<string, boolean>({
    source: () => this.chaveDeDispensa(),
    computation: (chave) => this.navegador && localStorage.getItem(chave) === '1',
  });

  protected readonly visivel = computed(
    () => !this.jaEhApp() && !this.dispensado() && (this.evento() !== null || this.ehIos())
  );

  constructor() {
    if (!this.navegador) {
      return;
    }

    // Rodando sem a barra do navegador ja e o app instalado, entao o convite nao faz sentido.
    const rodandoComoApp =
      window.matchMedia('(display-mode: standalone)').matches ||
      // Essa propriedade so existe no Safari do iPhone e nao esta no tipo padrao do Navigator.
      (window.navigator as Navigator & { standalone?: boolean }).standalone === true;

    if (rodandoComoApp) {
      this.jaEhApp.set(true);
      return;
    }

    const ua = window.navigator.userAgent;
    // O iPad novo se identifica como Mac. O que diferencia ele de um desktop e ter tela de toque.
    this.ehIos.set(
      /iPad|iPhone|iPod/.test(ua) ||
        (ua.includes('Macintosh') && window.navigator.maxTouchPoints > 1)
    );

    // Quem escuta o beforeinstallprompt e um script direto no index.html. Ele dispara antes
    // do Angular iniciar e nao acontece de novo na mesma carga da pagina, entao escutar la e
    // a unica forma de nao perder o evento. Aqui e so leitura do que ele guardou.
    const janela = window as Window & { __mealmathInstalacao?: EventoDeInstalacao | null };
    this.evento.set(janela.__mealmathInstalacao ?? null);
    window.addEventListener('mealmath:instalavel', () =>
      this.evento.set(janela.__mealmathInstalacao ?? null)
    );

    // Se instalou de qualquer jeito, ate pelo menu do navegador, a faixa some.
    window.addEventListener('appinstalled', () => this.jaEhApp.set(true));
  }

  /** Sem usuario na sessao a faixa nem aparece, porque a Shell so abre com login. */
  private chaveDeDispensa(): string {
    const id = this.auth.usuario()?.id;
    return id ? `${PREFIXO_DISPENSA}:${id}` : PREFIXO_DISPENSA;
  }

  protected async instalar(): Promise<void> {
    const evento = this.evento();
    if (!evento || this.instalando()) {
      return;
    }

    this.instalando.set(true);
    await evento.prompt();
    const { outcome } = await evento.userChoice;
    this.instalando.set(false);

    // O evento so pode ser usado uma vez: aceitando ou recusando, ele nao serve mais. Recusar
    // na janela do navegador nao grava nada aqui, o Chrome volta a oferecer depois.
    this.evento.set(null);
    if (outcome === 'accepted') {
      this.jaEhApp.set(true);
    }
  }

  protected dispensar(): void {
    if (this.navegador) {
      localStorage.setItem(this.chaveDeDispensa(), '1');
    }
    this.dispensado.set(true);
  }
}
