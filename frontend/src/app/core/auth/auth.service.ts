import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CadastroRequest, LoginRequest, TokenResponse, UsuarioResponse } from './auth.model';
import { TokenService } from './token.service';

/** Sessao guardada em memoria. O unico dado que sobrevive a um F5 e o JWT, no TokenService. */
interface Sessao {
  token: string;
  usuario: UsuarioResponse;
}

/** Claims que a API coloca no token: sub e o id do usuario, mais email, nome, criadoEm e exp. */
interface PayloadJwt {
  sub?: string;
  email?: string;
  nome?: string;
  /** Vem como texto ISO-8601 e nao como numero. */
  criadoEm?: string;
  exp?: number;
}

/**
 * Guarda o estado da sessao do usuario logado (RF001/RF002).
 * Fica so com o JWT e com quem esta logado. Nenhum dado do sistema passa por aqui.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenService);

  private readonly sessao = signal<Sessao | null>(this.restaurarSessao());

  readonly autenticado = computed(() => this.sessao() !== null);
  readonly usuario = computed(() => this.sessao()?.usuario ?? null);

  /** POST /auth/login (RF002). Abre a sessao antes de quem chamou receber a resposta. */
  entrar(credenciais: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/login`, credenciais)
      .pipe(tap((resposta) => this.abrirSessao(resposta)));
  }

  /** POST /auth/registrar (RF001). A API ja devolve o usuario logado. */
  registrar(dados: CadastroRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/registrar`, dados)
      .pipe(tap((resposta) => this.abrirSessao(resposta)));
  }

  abrirSessao(resposta: TokenResponse): void {
    this.tokens.gravar(resposta.token);
    this.sessao.set({ token: resposta.token, usuario: resposta.usuario });
  }

  encerrarSessao(): void {
    this.tokens.limpar();
    this.sessao.set(null);
  }

    /**
     * Remonta a sessao a partir do token salvo, para nao ter que logar de novo a cada F5. Os dados
     * saem das claims do proprio JWT, sem guardar o usuario duplicado no localStorage.
     */
  private restaurarSessao(): Sessao | null {
    const token = this.tokens.ler();
    if (!token) {
      return null;
    }

    const payload = lerPayload(token);
    // Token quebrado ou vencido nao vale como sessao. Descarto agora em vez de esperar o 401.
    if (!payload?.sub || expirado(payload)) {
      this.tokens.limpar();
      return null;
    }

    return {
      token,
      usuario: {
        id: Number(payload.sub),
        nome: payload.nome ?? '',
        email: payload.email ?? '',
        criadoEm: payload.criadoEm,
      },
    };
  }
}

function expirado(payload: PayloadJwt): boolean {
  // O exp vem em segundos e o Date.now() em milissegundos, por isso a multiplicacao.
  return payload.exp !== undefined && payload.exp * 1000 <= Date.now();
}

function lerPayload(token: string): PayloadJwt | null {
  const partes = token.split('.');
  if (partes.length !== 3) {
    return null;
  }

  try {
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = atob(base64);
    // Remonto o UTF-8 byte a byte, senao nome com acento sai quebrado do atob.
    const json = decodeURIComponent(
      Array.from(bytes, (c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')
    );
    return JSON.parse(json) as PayloadJwt;
  } catch {
    return null;
  }
}
