import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { TokenResponse } from '../../core/auth/auth.model';
import { Login } from './login';

const RESPOSTA: TokenResponse = {
  token: 'cabecalho.payload.assinatura',
  tipo: 'Bearer',
  expiraEmSegundos: 28800,
  usuario: { id: 1, nome: 'Kelvin', email: 'kelvin@exemplo.com' },
};

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let http: HttpTestingController;
  let navegar: jasmine.Spy;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function preencher(email: string, senha: string): void {
    fixture.componentInstance['form'].setValue({ email, senha });
    fixture.detectChanges();
  }

  function enviar(): void {
    const elemento = fixture.nativeElement as HTMLElement;
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('não chama a API com o formulário vazio', () => {
    enviar();

    http.expectNone(`${environment.apiUrl}/auth/login`);
    expect(navegar).not.toHaveBeenCalled();

    // Ao enviar, os campos sao marcados como tocados, entao o aviso aparece na hora.
    const avisos = (fixture.nativeElement as HTMLElement).querySelectorAll('.erro-campo');
    expect(Array.from(avisos, (aviso) => aviso.textContent?.trim())).toEqual([
      'Informe seu e-mail.',
      'Informe sua senha.',
    ]);
  });

  it('leva para o dashboard depois de autenticar', () => {
    preencher('kelvin@exemplo.com', 'senhaSegura1');
    enviar();

    const requisicao = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(requisicao.request.body).toEqual({
      email: 'kelvin@exemplo.com',
      senha: 'senhaSegura1',
    });
    requisicao.flush(RESPOSTA);

    expect(navegar).toHaveBeenCalledWith('/dashboard');
  });

  it('mostra a mensagem da API quando a credencial é recusada', () => {
    preencher('kelvin@exemplo.com', 'errada');
    enviar();

    http
      .expectOne(`${environment.apiUrl}/auth/login`)
      .flush({ mensagem: 'E-mail ou senha inválidos.' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    const alerta = (fixture.nativeElement as HTMLElement).querySelector('.erro-form');
    expect(alerta?.textContent?.trim()).toBe('E-mail ou senha inválidos.');
    expect(navegar).not.toHaveBeenCalled();
  });
});
