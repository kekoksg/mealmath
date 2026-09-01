import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DataIso } from '../../core/dominio/data';
import {
  ItemRegistroQuantidadeRequest,
  RegistroDiarioRequest,
  RegistroDiarioResponse,
} from './registro-diario.model';

/** Chama a rota /registros-diarios, o que foi comido de verdade em cada dia (RF008). */
@Injectable({ providedIn: 'root' })
export class RegistroDiarioService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/registros-diarios`;

  /** O intervalo inclui as duas pontas. O backend ja devolve ordenado por data e id. */
  listarPorIntervalo(inicio: DataIso, fim: DataIso): Observable<RegistroDiarioResponse[]> {
    return this.http.get<RegistroDiarioResponse[]>(this.url, { params: { inicio, fim } });
  }

  /** Joga um modelo da biblioteca na data. Quem faz a copia dos itens e o backend. */
  registrar(requisicao: RegistroDiarioRequest): Observable<RegistroDiarioResponse> {
    return this.http.post<RegistroDiarioResponse>(this.url, requisicao);
  }

  /**
   * Muda a quantidade de um item so naquele dia. A resposta vem com o registro inteiro ja
   * recalculado, entao e melhor usar ela do que tentar corrigir o custo aqui na tela.
   */
  ajustarItem(
    registroId: number,
    itemId: number,
    requisicao: ItemRegistroQuantidadeRequest
  ): Observable<RegistroDiarioResponse> {
    return this.http.patch<RegistroDiarioResponse>(
      `${this.url}/${registroId}/itens/${itemId}`,
      requisicao
    );
  }

  /** Tira a refeicao do dia. O modelo continua na biblioteca. */
  remover(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

    /**
     * Repetir o dia e repetir o que foi comido, ja com os ajustes de quantidade, e nao o que o modelo
     * diz. Responde 400 quando o dia anterior esta vazio.
     */
  duplicarDiaAnterior(data: DataIso): Observable<RegistroDiarioResponse[]> {
    return this.http.post<RegistroDiarioResponse[]>(`${this.url}/duplicar-dia-anterior`, { data });
  }
}
