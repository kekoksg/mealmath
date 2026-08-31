import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { TokenResponse } from './auth.model';
import { AuthService } from './auth.service';

const CHAVE = 'dieta.token';

/** Monta um JWT com formato valido. A assinatura e falsa, mas o front nao confere isso. */
function jwtFalso(payload: Record<string, unknown>): string {
  const json = JSON.stringify(payload);
  const bytes = new TextEncoder().encode(json);
  const base64 = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `cabecalho.${base64}.assinatura`;
}

function emSegundos(deslocamento: number): number {
  return Math.floor(Date.now() / 1000) + deslocamento;
}

const CRIADO_EM = '2026-01-15T10:30:00Z';

const RESPOSTA: TokenResponse = {
  token: jwtFalso({
    sub: '1',
    nome: 'Kelvin',
    email: 'kelvin@exemplo.com',
    criadoEm: CRIADO_EM,
    exp: emSegundos(28800),
  }),
  tipo: 'Bearer',
  expiraEmSegundos: 28800,
  usuario: { id: 1, nome: 'Kelvin', email: 'kelvin@exemplo.com', criadoEm: CRIADO_EM },
};

describe('AuthService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem(CHAVE);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CHAVE);
  });

  it('começa sem sessão quando não há token gravado', () => {
    const auth = TestBed.inject(AuthService);

    expect(auth.autenticado()).toBeFalse();
    expect(auth.usuario()).toBeNull();
  });

  it('abre a sessão e grava o token no login', () => {
    const auth = TestBed.inject(AuthService);

    auth.entrar({ email: 'kelvin@exemplo.com', senha: 'senhaSegura1' }).subscribe();

    const requisicao = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(requisicao.request.method).toBe('POST');
    requisicao.flush(RESPOSTA);

    expect(auth.autenticado()).toBeTrue();
    expect(auth.usuario()?.nome).toBe('Kelvin');
    expect(localStorage.getItem(CHAVE)).toBe(RESPOSTA.token);
  });

  it('abre a sessão direto no cadastro, sem passar pelo login', () => {
    const auth = TestBed.inject(AuthService);

    auth
      .registrar({ nome: 'Kelvin', email: 'kelvin@exemplo.com', senha: 'senhaSegura1' })
      .subscribe();

    http.expectOne(`${environment.apiUrl}/auth/registrar`).flush(RESPOSTA, { status: 201, statusText: 'Created' });

    expect(auth.autenticado()).toBeTrue();
    expect(localStorage.getItem(CHAVE)).toBe(RESPOSTA.token);
  });

  it('não abre sessão quando a API recusa a credencial', () => {
    const auth = TestBed.inject(AuthService);

    auth.entrar({ email: 'kelvin@exemplo.com', senha: 'errada' }).subscribe({ error: () => {} });

    http
      .expectOne(`${environment.apiUrl}/auth/login`)
      .flush({ mensagem: 'E-mail ou senha inválidos.' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.autenticado()).toBeFalse();
    expect(localStorage.getItem(CHAVE)).toBeNull();
  });

  it('encerra a sessão e apaga o token', () => {
    const auth = TestBed.inject(AuthService);
    auth.abrirSessao(RESPOSTA);

    auth.encerrarSessao();

    expect(auth.autenticado()).toBeFalse();
    expect(auth.usuario()).toBeNull();
    expect(localStorage.getItem(CHAVE)).toBeNull();
  });

  it('restaura a sessão a partir do token gravado, sem novo login', () => {
    localStorage.setItem(CHAVE, RESPOSTA.token);

    const auth = TestBed.inject(AuthService);

    expect(auth.autenticado()).toBeTrue();
    expect(auth.usuario()).toEqual({
      id: 1,
      nome: 'Kelvin',
      email: 'kelvin@exemplo.com',
      criadoEm: CRIADO_EM,
    });
  });

  it('sessão restaurada de token sem a claim criadoEm continua válida', () => {
    // Token emitido antes desse campo existir. A sessao vale ate expirar e o perfil so
    // deixa de mostrar o "Membro desde", em vez de inventar uma data.
    localStorage.setItem(
      CHAVE,
      jwtFalso({ sub: '1', nome: 'Kelvin', email: 'kelvin@exemplo.com', exp: emSegundos(28800) })
    );

    const auth = TestBed.inject(AuthService);

    expect(auth.autenticado()).toBeTrue();
    expect(auth.usuario()?.criadoEm).toBeUndefined();
  });

  it('descarta token vencido em vez de esperar o 401', () => {
    localStorage.setItem(CHAVE, jwtFalso({ sub: '1', nome: 'Kelvin', exp: emSegundos(-60) }));

    const auth = TestBed.inject(AuthService);

    expect(auth.autenticado()).toBeFalse();
    expect(localStorage.getItem(CHAVE)).toBeNull();
  });

  it('descarta token ilegível', () => {
    localStorage.setItem(CHAVE, 'nao-e-um-jwt');

    const auth = TestBed.inject(AuthService);

    expect(auth.autenticado()).toBeFalse();
    expect(localStorage.getItem(CHAVE)).toBeNull();
  });
});
