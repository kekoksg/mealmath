import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Icone } from '../../shared/icone/icone';

interface ItemNav {
  readonly rota: string;
  /** Nome do icone no mapa de shared/icone, nao o desenho em si. */
  readonly icone: string;
  readonly rotulo: string;
}

@Component({
  selector: 'app-bottom-nav',
  imports: [RouterLink, RouterLinkActive, Icone],
  templateUrl: './bottom-nav.html',
  styleUrl: './bottom-nav.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomNav {
  /**
   * O perfil nao esta nessa lista de proposito. Ele e ajuste de conta e nao uma das quatro
   * tarefas do dia a dia, entao fica no canto de cima do dashboard.
   */
  protected readonly itens: readonly ItemNav[] = [
    { rota: '/dashboard', icone: 'inicio', rotulo: 'Início' },
    { rota: '/diario', icone: 'diario', rotulo: 'Diário' },
    { rota: '/refeicoes', icone: 'refeicao', rotulo: 'Refeição' },
    { rota: '/mercado', icone: 'mercado', rotulo: 'Mercado' },
  ];
}
