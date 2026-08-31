import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  HistoricoPrecoResponse,
  ItemMercadoRequest,
  ItemMercadoResponse,
} from './item-mercado.model';

/** Chama a rota /itens-mercado, do cadastro (RF004) e da atualizacao de preco (RF007). */
@Injectable({ providedIn: 'root' })
export class ItemMercadoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/itens-mercado`;

  /** Traz so os itens ativos, ja ordenados por nome pelo backend. */
  listar(): Observable<ItemMercadoResponse[]> {
    return this.http.get<ItemMercadoResponse[]>(this.url);
  }

  criar(item: ItemMercadoRequest): Observable<ItemMercadoResponse> {
    return this.http.post<ItemMercadoResponse>(this.url, item);
  }

  /** Mudando preco, embalagem ou unidade, o backend salva o valor antigo no historico. */
  atualizar(id: number, item: ItemMercadoRequest): Observable<ItemMercadoResponse> {
    return this.http.put<ItemMercadoResponse>(`${this.url}/${id}`, item);
  }

  /** Exclusao logica. O item continua no banco porque o diario aponta para ele. */
  desativar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Do mais novo para o mais antigo. Volta lista vazia quando o preco nunca mudou. */
  historico(id: number): Observable<HistoricoPrecoResponse[]> {
    return this.http.get<HistoricoPrecoResponse[]>(`${this.url}/${id}/historico`);
  }
}
