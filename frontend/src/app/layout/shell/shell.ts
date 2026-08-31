import { ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { InstalarApp } from '../../shared/instalar-app/instalar-app';
import { BottomNav } from '../bottom-nav/bottom-nav';

/** Estrutura das telas de dentro: faixa de instalacao, area que rola e o menu fixo embaixo. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, BottomNav, InstalarApp],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly router = inject(Router);

  /** Opcional e nao required porque no SSR esse elemento nao existe. */
  private readonly area = viewChild<ElementRef<HTMLElement>>('area');

  constructor() {
    // Quem rola aqui e a div .screen e nao a janela, e o Router so sabe restaurar a rolagem
    // da janela. Sem isso, trocar de aba mantem a rolagem da tela anterior e a nova abre no
    // meio, com o titulo cortado. App de celular sempre abre no topo.
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => this.area()?.nativeElement.scrollTo({ top: 0 }));
  }
}
