import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardResponse, PeriodoDashboard } from './dashboard.model';

/** Chama a rota /dashboard, que traz o custo consolidado por periodo (RF006). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/dashboard`;

  /** Responde 200 sempre, ate sem registro nenhum. Periodo vazio e normal e nao e erro. */
  consolidar(periodo: PeriodoDashboard): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.url, {
      params: new HttpParams().set('periodo', periodo),
    });
  }
}
