import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UsuarioResponse } from '../../core/auth/auth.model';
import { AuthService } from '../../core/auth/auth.service';
import { InstalarApp } from './instalar-app';

function conta(id: number): UsuarioResponse {
  return { id, nome: `Pessoa ${id}`, email: `pessoa${id}@email.com` };
}

/** Evento do Chrome que faz a faixa aparecer. Nenhum teste chega a disparar ele. */
const EVENTO_FALSO = {
  prompt: () => Promise.resolve(),
  userChoice: Promise.resolve({ outcome: 'dismissed' as const }),
};

type JanelaComEvento = Window & { __mealmathInstalacao?: unknown };

describe('Convite de instalação', () => {
  let fixture: ComponentFixture<InstalarApp>;
  const usuario = signal<UsuarioResponse | null>(conta(1));

  beforeEach(async () => {
    localStorage.clear();
    usuario.set(conta(1));
    (window as JanelaComEvento).__mealmathInstalacao = EVENTO_FALSO;

    await TestBed.configureTestingModule({
      imports: [InstalarApp],
      providers: [{ provide: AuthService, useValue: { usuario } }],
    }).compileComponents();

    fixture = TestBed.createComponent(InstalarApp);
    fixture.detectChanges();
  });

  afterEach(() => {
    delete (window as JanelaComEvento).__mealmathInstalacao;
    localStorage.clear();
  });

  function faixa(): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.instalar');
  }

  it('a recusa vale por conta, não pelo navegador', () => {
    expect(faixa()).not.toBeNull();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.x')!.click();
    fixture.detectChanges();
    expect(faixa()).toBeNull();

    // Outra pessoa no mesmo aparelho nunca foi convidada. Guardando a recusa so por
    // navegador, o convite dela ficaria escondido.
    usuario.set(conta(2));
    fixture.detectChanges();
    expect(faixa()).not.toBeNull();

    // E voltando para a primeira conta, o que ela decidiu continua valendo.
    usuario.set(conta(1));
    fixture.detectChanges();
    expect(faixa()).toBeNull();
  });

  it('a recusa sobrevive a uma nova visita da mesma conta', () => {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.x')!.click();
    fixture.detectChanges();

    // Componente novo lendo o mesmo armazenamento, que e o que acontece ao reabrir o app.
    const outra = TestBed.createComponent(InstalarApp);
    outra.detectChanges();
    expect((outra.nativeElement as HTMLElement).querySelector('.instalar')).toBeNull();
  });
});
