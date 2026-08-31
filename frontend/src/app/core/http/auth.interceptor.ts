import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { TokenService } from '../auth/token.service';
import { environment } from '../../../environments/environment';

/** Endpoints que geram o token. Nao faz sentido mandar Authorization neles. */
const ROTAS_PUBLICAS = ['/auth/login', '/auth/registrar'];

/**
 * Coloca o JWT nas chamadas da API e derruba a sessao quando volta 401.
 * So mexe nas requisicoes para a apiUrl. Imagens e chamadas de terceiros passam direto.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenService);
  const auth = inject(AuthService);
  const router = inject(Router);

  const paraApi = req.url.startsWith(environment.apiUrl);
  const publica = ROTAS_PUBLICAS.some((rota) => req.url.includes(rota));
  const token = tokens.ler();

  const requisicao =
    paraApi && !publica && token
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(requisicao).pipe(
    catchError((erro: HttpErrorResponse) => {
      if (erro.status === 401 && paraApi && !publica) {
        auth.encerrarSessao();
        router.navigate(['/login']);
      }
      return throwError(() => erro);
    })
  );
};
