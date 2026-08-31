import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    // A aplicacao inteira exige login. O guard depende do token, que so existe no navegador,
    // e nao tem conteudo publico para gerar antes. Com prerender toda rota viraria o HTML da
    // tela de login.
    path: '**',
    renderMode: RenderMode.Client,
  },
];
