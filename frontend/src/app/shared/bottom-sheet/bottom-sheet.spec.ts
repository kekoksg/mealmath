import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { BottomSheet } from './bottom-sheet';

/**
 * Componente minimo so para hospedar o painel nos testes. O painel so existe enquanto aberto
 * for true, que e como as telas usam. O botao de fora e para onde o foco tem que voltar.
 */
@Component({
  imports: [BottomSheet],
  template: `
    <button id="gatilho" type="button" (click)="aberto.set(true)">Abrir</button>

    @if (aberto()) {
      <app-bottom-sheet [titulo]="titulo()" (fechar)="aberto.set(false)">
        <input id="campo" />
        <button id="salvar" type="button">Salvar</button>
      </app-bottom-sheet>
    }
  `,
})
class Hospedeiro {
  readonly aberto = signal(false);
  readonly titulo = signal('Primeiro passo');
}

describe('BottomSheet', () => {
  let fixture: ComponentFixture<Hospedeiro>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Hospedeiro] }).compileComponents();
    fixture = TestBed.createComponent(Hospedeiro);
    // Precisa estar no documento de verdade, porque o foco nao anda em elemento solto.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => fixture.nativeElement.remove());

  function tela(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function elemento(seletor: string): HTMLElement {
    return tela().querySelector<HTMLElement>(seletor)!;
  }

  function abrir(): void {
    elemento('#gatilho').focus();
    elemento('#gatilho').click();
    fixture.detectChanges();
  }

  function teclar(tecla: string, shiftKey = false): KeyboardEvent {
    const evento = new KeyboardEvent('keydown', { key: tecla, shiftKey, cancelable: true });
    document.dispatchEvent(evento);
    fixture.detectChanges();
    return evento;
  }

  it('leva o foco para o painel ao abrir', () => {
    abrir();

    // Quem recebe o foco e o painel e nao o primeiro campo, para o leitor de tela anunciar o
    // titulo antes do usuario comecar a preencher.
    expect(document.activeElement).toBe(elemento('.sheet'));
  });

  it('devolve o foco a quem abriu o painel ao fechá-lo', () => {
    abrir();

    teclar('Escape');

    expect(tela().querySelector('.sheet')).toBeNull();
    expect(document.activeElement).toBe(elemento('#gatilho'));
  });

  it('circula o Tab dentro do painel em vez de deixá-lo cair no fundo', () => {
    abrir();

    // Do ultimo controle o Tab volta para o primeiro, que e o botao de fechar, em vez de ir
    // para o conteudo atras do veu.
    elemento('#salvar').focus();
    expect(teclar('Tab').defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(elemento('.sh-h button'));

    // Shift+Tab no primeiro elemento fecha o ciclo pelo outro lado.
    expect(teclar('Tab', true).defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(elemento('#salvar'));
  });

  it('traz de volta o foco que caiu no body ao trocar de passo', () => {
    abrir();

    // Quando um botao e removido, o foco cai no body, e dai em diante o Tab percorreria a
    // pagina inteira por baixo do veu.
    (document.activeElement as HTMLElement).blur();
    expect(document.activeElement).toBe(document.body);

    expect(teclar('Tab').defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(elemento('.sh-h button'));
  });

  it('Tab a partir do próprio painel segue o caminho natural, para o primeiro controle', () => {
    abrir();

    // O painel vem antes do conteudo no DOM, entao o navegador ja entra nele sozinho e
    // interceptar aqui so atrapalharia.
    expect(teclar('Tab').defaultPrevented).toBeFalse();
  });

  it('recoloca o foco no painel quando o passo muda', () => {
    abrir();
    elemento('#salvar').focus();

    // Em um painel de varias etapas o conteudo troca sem recriar o componente. Sem isso o
    // foco ficaria em um botao que o passo anterior levou embora.
    fixture.componentInstance.titulo.set('Segundo passo');
    fixture.detectChanges();

    expect(document.activeElement).toBe(elemento('.sheet'));
    expect(elemento('.sh-h h3').textContent?.trim()).toBe('Segundo passo');
  });

  it('fecha ao clicar no véu, mas não ao clicar dentro do painel', () => {
    abrir();

    elemento('.sheet').click();
    fixture.detectChanges();
    expect(tela().querySelector('.sheet')).not.toBeNull();

    elemento('.scrim').click();
    fixture.detectChanges();
    expect(tela().querySelector('.sheet')).toBeNull();
  });

  /** Simula o dedo descendo a distancia informada, em pixels, a partir da alca. */
  function arrastar(distancia: number, soltar = true): void {
    const puxador = elemento('.puxador');
    // O setPointerCapture precisa de um ponteiro de verdade, que no teste nao existe.
    puxador.setPointerCapture = () => undefined;

    puxador.dispatchEvent(new PointerEvent('pointerdown', { clientY: 0, bubbles: true }));
    puxador.dispatchEvent(new PointerEvent('pointermove', { clientY: distancia, bubbles: true }));
    if (soltar) {
      puxador.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    }
    fixture.detectChanges();
  }

  it('arrastar além do limiar desce o painel até o fim antes de fechar', () => {
    abrir();

    arrastar(140);

    // Continua na tela terminando de descer. Sumir no meio do gesto parece travamento.
    const painel = tela().querySelector<HTMLElement>('.sheet');
    expect(painel).not.toBeNull();
    expect(painel!.style.transform).toBe('translateY(100%)');
    expect(elemento('.scrim').classList).toContain('saindo');

    // Quem remove o painel e o fim da descida.
    painel!.dispatchEvent(new TransitionEvent('transitionend', { propertyName: 'transform' }));
    fixture.detectChanges();
    expect(tela().querySelector('.sheet')).toBeNull();
  });

  it('fecha mesmo se o fim da animação não for anunciado', fakeAsync(() => {
    abrir();

    arrastar(140);
    expect(tela().querySelector('.sheet')).not.toBeNull();

    // Com a aba em segundo plano a transicao nao termina, e sem esse tempo limite o painel
    // ficaria preso na tela para sempre.
    tick(400);
    fixture.detectChanges();
    expect(tela().querySelector('.sheet')).toBeNull();
  }));

  it('transição de um filho não fecha o painel no meio do caminho', () => {
    abrir();

    arrastar(140);

    elemento('#salvar').dispatchEvent(
      new TransitionEvent('transitionend', { propertyName: 'transform', bubbles: true })
    );
    fixture.detectChanges();

    expect(tela().querySelector('.sheet')).not.toBeNull();
  });

  it('arraste curto devolve o painel ao lugar em vez de fechar', () => {
    abrir();

    arrastar(30);

    // Arrastar pouco nao pode fechar. O painel volta e o que estava sendo editado continua la.
    expect(tela().querySelector('.sheet')).not.toBeNull();
    expect(elemento('.sheet').style.transform).toBe('');
  });

  it('o painel acompanha o dedo enquanto o gesto não termina', () => {
    abrir();

    arrastar(50, false);

    expect(elemento('.sheet').style.transform).toBe('translateY(50px)');
    expect(elemento('.sheet').classList).toContain('arrastando');
  });

  it('puxar para cima não estica o painel', () => {
    abrir();

    arrastar(-80, false);

    expect(elemento('.sheet').style.transform).toBe('');
  });

  it('pressionar o ✕ dentro da área de arraste é clique, não gesto', () => {
    abrir();

    const fechar = elemento('.sh-h button');
    fechar.dispatchEvent(new PointerEvent('pointerdown', { clientY: 0, bubbles: true }));
    elemento('.puxador').dispatchEvent(
      new PointerEvent('pointermove', { clientY: 60, bubbles: true })
    );
    fixture.detectChanges();

    // Sem isso, tocar no botao de fechar arrastaria o painel junto antes de fechar.
    expect(elemento('.sheet').style.transform).toBe('');
  });
});
