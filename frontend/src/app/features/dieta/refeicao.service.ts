import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RefeicaoRequest, RefeicaoResponse } from './refeicao.model';

/** Chama a rota /refeicoes, que e a biblioteca de refeicoes modelo (RF003). */
@Injectable({ providedIn: 'root' })
export class RefeicaoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/refeicoes`;

  /** Traz as refeicoes do usuario do token, ja ordenadas por titulo pelo backend. */
  listar(): Observable<RefeicaoResponse[]> {
    return this.http.get<RefeicaoResponse[]>(this.url);
  }

  criar(refeicao: RefeicaoRequest): Observable<RefeicaoResponse> {
    return this.http.post<RefeicaoResponse>(this.url, refeicao);
  }

  /** A lista de itens substitui tudo. O que nao for enviado e removido da refeicao. */
  atualizar(id: number, refeicao: RefeicaoRequest): Observable<RefeicaoResponse> {
    return this.http.put<RefeicaoResponse>(`${this.url}/${id}`, refeicao);
  }

  /** Os registros do diario que ja existem nao mudam, porque eles tem copia propria dos itens. */
  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
