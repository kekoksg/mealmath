import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MetaOrcamentoRequest, MetaOrcamentoResponse } from './dashboard.model';

/**
 * Chama a rota /meta-orcamento (RF010). O dashboard ja recebe o progresso calculado dentro da
 * resposta dele, entao quem chama aqui esta gravando e nao consultando.
 */
@Injectable({ providedIn: 'root' })
export class MetaOrcamentoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/meta-orcamento`;

  /** Devolve null quando nao tem meta definida, porque a API responde 204 sem corpo. */
  buscar(): Observable<MetaOrcamentoResponse | null> {
    return this.http.get<MetaOrcamentoResponse | null>(this.url);
  }

  /** PUT porque sobrescreve a meta inteira. Volta 201 na primeira vez e 200 nas outras. */
  definir(meta: MetaOrcamentoRequest): Observable<MetaOrcamentoResponse> {
    return this.http.put<MetaOrcamentoResponse>(this.url, meta);
  }

  /** Depois disso o dashboard volta a esconder o progresso e a oferecer o botao de definir. */
  remover(): Observable<void> {
    return this.http.delete<void>(this.url);
  }
}
