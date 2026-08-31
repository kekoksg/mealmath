import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * Endereco da API Spring. So precisa ser acessivel por esse processo aqui, porque o backend
 * fica em localhost e nunca e exposto na internet.
 */
const apiOrigem = process.env['BACKEND_URL'] ?? 'http://localhost:8082';

/**
 * Cabecalhos que descrevem a conexao e nao a mensagem, entao nao sao repassados. O
 * content-length em especial fica errado quando o corpo e reenviado.
 */
const CABECALHOS_DE_CONEXAO = new Set([
  'host',
  'connection',
  'content-length',
  'transfer-encoding',
]);

/**
 * Cabecalhos do navegador que param aqui. O que importa e o origin: repassando ele, o
 * filtro de CORS do Spring compararia a origem publica com a lista de origens permitidas e
 * responderia 403, mesmo a chamada sendo da mesma origem do ponto de vista do navegador, que
 * ja tinha autorizado antes de sair. Daqui para a API e servidor para servidor, onde CORS
 * nem se aplica.
 */
const CABECALHOS_DO_NAVEGADOR = new Set(['origin', 'referer']);

/**
 * Repassa tudo que chega em /api para a API Spring.
 *
 * Em producao o front usa apiUrl: '/api', ou seja, o navegador so conversa com esse servidor. Assim
 * a API nao precisa estar publicada e nao ha CORS no meio. O prefixo /api sai do caminho porque as
 * rotas do backend ficam na raiz.
 *
 * Vem antes dos arquivos estaticos e do renderizador, senao uma chamada de API cairia no Angular e
 * voltaria como HTML.
 */
app.use('/api', (req, res) => {
  const alvo = new URL(req.url || '/', apiOrigem);

  const cabecalhos = new Headers();
  for (const [nome, valor] of Object.entries(req.headers)) {
    if (valor === undefined || CABECALHOS_DE_CONEXAO.has(nome) || CABECALHOS_DO_NAVEGADOR.has(nome)) {
      continue;
    }
    cabecalhos.set(nome, Array.isArray(valor) ? valor.join(', ') : valor);
  }

  const temCorpo = req.method !== 'GET' && req.method !== 'HEAD';

  fetch(alvo, {
    method: req.method,
    headers: cabecalhos,
    // O duplex: 'half' e exigido para enviar o corpo em streaming. Sem ele, a requisicao
    // que tem corpo e recusada antes de sair.
    body: temCorpo ? (req as unknown as BodyInit) : undefined,
    duplex: 'half',
    redirect: 'manual',
  } as RequestInit)
    .then(async (resposta) => {
      res.status(resposta.status);
      resposta.headers.forEach((valor, nome) => {
        // O corpo ja chega descomprimido aqui. Repassando o content-encoding original,
        // o navegador tentaria descomprimir um texto que ja esta normal.
        if (!CABECALHOS_DE_CONEXAO.has(nome) && nome !== 'content-encoding') {
          res.setHeader(nome, valor);
        }
      });
      res.end(Buffer.from(await resposta.arrayBuffer()));
    })
    .catch((erro: unknown) => {
      // API fora do ar nao pode virar uma pagina de erro em HTML. O front espera JSON e
      // trataria isso como sessao invalida, deslogando o usuario sem motivo.
      console.error(`Falha ao encaminhar ${req.method} ${alvo.pathname} para a API:`, erro);
      res.status(502).json({ mensagem: 'API indisponível no momento.' });
    });
});

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url)) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
