import {
  ApplicationConfig,
  isDevMode,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideServiceWorker } from '@angular/service-worker';

import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Faz a transicao suave entre as abas do menu de baixo, usando a API do proprio
    // navegador. Onde nao tem suporte, como no Safari mais antigo, a troca volta a ser
    // direta e nada quebra, porque isso e so um detalhe visual.
    provideRouter(routes, withComponentInputBinding(), withViewTransitions()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideClientHydration(withEventReplay()),
    // Guarda o HTML, o CSS, o JS e as fontes no cache, entao a segunda abertura e
    // instantanea e funciona ate sem internet. Os dados continuam vindo sempre da API,
    // porque /api fica fora do service worker no ngsw-config.json. Sem isso,
    // o custo na tela poderia ser o de ontem sem o usuario perceber.
    //
    // Fica desligado em desenvolvimento, senao o worker guardaria a versao antiga e
    // esconderia a alteracao recem-salva.
    //
    // O registerWhenStable espera a aplicacao carregar antes de registrar, para o download
    // do worker nao disputar internet com a primeira exibicao da tela.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
