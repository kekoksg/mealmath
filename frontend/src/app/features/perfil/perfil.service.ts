import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenResponse } from '../../core/auth/auth.model';
import { AuthService } from '../../core/auth/auth.service';
import { AlterarSenhaRequest, PerfilRequest } from './perfil.model';

/**
 * Chama a rota /perfil, com os dados da propria conta.
 *
 * As duas gravacoes devolvem token novo porque nome e e-mail ficam dentro do JWT, e e dele que a
 * sessao e remontada no F5. Por isso a sessao e reaberta aqui, e nao em cada componente.
 */
@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly url = `${environment.apiUrl}/perfil`;

  atualizar(dados: PerfilRequest): Observable<TokenResponse> {
    return this.http
      .put<TokenResponse>(this.url, dados)
      .pipe(tap((resposta) => this.auth.abrirSessao(resposta)));
  }

  alterarSenha(dados: AlterarSenhaRequest): Observable<TokenResponse> {
    return this.http
      .put<TokenResponse>(`${this.url}/senha`, dados)
      .pipe(tap((resposta) => this.auth.abrirSessao(resposta)));
  }
}
