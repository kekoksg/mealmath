import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const CHAVE = 'dieta.token';

/**
 * Guarda so o JWT da sessao. Dado do sistema (refeicao, item, registro, meta) nunca e salvo
 * aqui, porque quem manda e a API.
 */
@Injectable({ providedIn: 'root' })
export class TokenService {
  private readonly navegador = isPlatformBrowser(inject(PLATFORM_ID));

  ler(): string | null {
    if (!this.navegador) {
      return null;
    }
    return localStorage.getItem(CHAVE);
  }

  gravar(token: string): void {
    if (this.navegador) {
      localStorage.setItem(CHAVE, token);
    }
  }

  limpar(): void {
    if (this.navegador) {
      localStorage.removeItem(CHAVE);
    }
  }
}
