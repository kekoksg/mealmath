import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { ICONES } from './icones';

/**
 * Desenha um icone do Solar Icon Set direto no HTML.
 *
 * Nao recebe tamanho nem cor: o SVG mede 1em e usa currentColor, entao herda os dois de onde
 * estiver. O mesmo icone aparece pequeno no menu e grande num cartao, sem configurar nada.
 *
 * Nome fora do mapa e mostrado como texto. Refeicoes anteriores a essa troca tem emoji salvo no
 * banco, que assim continua aparecendo enquanto a migracao nao roda.
 */
@Component({
  selector: 'app-icone',
  template: `
    @if (desenho(); as icone) {
      <svg
        [attr.viewBox]="icone.caixa"
        [innerHTML]="corpo()"
        xmlns="http://www.w3.org/2000/svg"
        focusable="false"
        aria-hidden="true"
      ></svg>
    } @else {
      <span class="legado" aria-hidden="true">{{ nome() }}</span>
    }
  `,
  styleUrl: './icone.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icone {
  private readonly sanitizador = inject(DomSanitizer);

  readonly nome = input.required<string>();

  protected readonly desenho = computed(() => ICONES[this.nome()] ?? null);

  /**
   * O desenho vem do arquivo gerado no build e nunca de algo digitado pelo usuario. Por isso
   * da para desligar o sanitizador aqui com seguranca. Sem isso ele apagaria as tags do SVG.
   */
  protected readonly corpo = computed(() => {
    const icone = this.desenho();
    return icone ? this.sanitizador.bypassSecurityTrustHtml(icone.corpo) : null;
  });
}
