/**
 * Captura as telas do app em PNG, para os tópicos 9.7 (passo-a-passo) e 10.1 do documento.
 *
 * Sobe um Chrome headless, autentica a conta de exemplo do seed e fotografa cada rota na
 * largura de referência do projeto — 390px, o mesmo alvo mobile-first do design. Sem isso
 * as imagens sairiam em largura de desktop e não representariam o produto.
 *
 * Pré-requisitos: API em :8082 com perfil dev (o seed é quem cria a conta abaixo) e
 * `ng serve` em :4200.
 *
 * Uso: node --experimental-websocket scripts/capturar-telas.mjs
 * A flag existe porque o WebSocket ainda é experimental no Node 20; some num major novo.
 */
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as esperar } from 'node:timers/promises';

const APP = 'http://localhost:4200';
const API = 'http://localhost:8082';
const PORTA_CDP = 9333;
const SAIDA = new URL('../../docs/telas/', import.meta.url);

/** Conta de exemplo criada por SeedDesenvolvimento — fixture de dev, não é conta real. */
const CONTA = { email: 'maria@email.com', senha: 'senha123' };

/** Chave usada por core/auth/token.service.ts. */
const CHAVE_TOKEN = 'dieta.token';

/** Largura de referência do design: 390 px, mobile-first. A altura recorta a dobra do celular. */
const VIEWPORT = { width: 390, height: 844 };

/** Clique no primeiro elemento que casa com o seletor. */
const clicar = (seletor) => `(() => {
  const alvo = document.querySelector(${JSON.stringify(seletor)});
  if (!alvo) throw new Error('não achei ' + ${JSON.stringify(seletor)});
  alvo.click();
  return true;
})()`;

/**
 * Clique pelo texto visível.
 *
 * As telas repetem a mesma classe em botões diferentes (`.info` aparece em meta, alertas,
 * senha e data de cadastro), então índice quebraria assim que a ordem mudasse. O texto é o
 * que identifica o botão para quem usa, e é o critério mais estável aqui.
 */
const clicarTexto = (texto) => `(() => {
  const alvo = [...document.querySelectorAll('button')]
    .find((b) => b.textContent.includes(${JSON.stringify(texto)}));
  if (!alvo) throw new Error('não achei botão com texto ' + ${JSON.stringify(texto)});
  alvo.click();
  return true;
})()`;

/**
 * Telas na ordem em que o passo-a-passo as apresenta.
 *
 * As sete primeiras são as rotas; as demais abrem os fluxos de criação e edição, que é onde
 * o cálculo fracionado (RF005) e o histórico de preço (RF007) ficam visíveis — as listas
 * mostram só o resultado. `passos` são cliques executados depois que a rota carrega.
 */
const TELAS = [
  { arquivo: '01-login', rota: '/login', sessao: false },
  { arquivo: '02-cadastro', rota: '/cadastro', sessao: false },
  { arquivo: '03-dashboard', rota: '/dashboard' },

  // RF006 nas três janelas. O indicador "Dias registrados: n/m" só existe em Semana e Mês —
  // em Hoje ele seria 1/1. `aguardar` é obrigatório aqui: ao trocar de período a tela segue
  // mostrando os números do período anterior até a resposta chegar, e sem esperar o rótulo
  // mudar a figura sairia com o dado errado sob o título certo.
  {
    arquivo: '03b-dashboard-semana',
    rota: '/dashboard',
    passos: [clicarTexto('Semana')],
    aguardar: `document.querySelector('.hero .lbl')?.textContent.includes('Semana')`,
  },
  {
    arquivo: '03c-dashboard-mes',
    rota: '/dashboard',
    passos: [clicarTexto('Mês')],
    aguardar: `document.querySelector('.hero .lbl')?.textContent.includes('Mês')`,
  },
  { arquivo: '04-mercado', rota: '/mercado' },
  { arquivo: '05-refeicoes', rota: '/refeicoes' },
  { arquivo: '06-diario', rota: '/diario' },
  { arquivo: '07-perfil', rota: '/perfil' },

  // RF004/RF007 — cadastro do item e a tela onde o preço muda e vira histórico.
  { arquivo: '08-mercado-novo-item', rota: '/mercado', passos: [clicar('.fab')] },
  // Peito de frango de propósito: é um dos itens que já teve reajuste, então a sheet mostra o
  // histórico (RF007). O primeiro da lista nunca mudou de preço e a figura não provaria nada.
  {
    arquivo: '09-mercado-editar-item',
    rota: '/mercado',
    passos: [clicarTexto('Peito de frango')],
  },

  // RF003/RF005 — montar a refeição-modelo e informar a quantidade consumida de um item.
  { arquivo: '10-refeicao-nova', rota: '/refeicoes', passos: [clicar('.fab')] },
  {
    arquivo: '11-refeicao-escolher-item',
    rota: '/refeicoes',
    passos: [clicar('.fab'), clicarTexto('Adicionar item do mercado')],
  },
  {
    arquivo: '12-refeicao-quantidade',
    rota: '/refeicoes',
    passos: [clicar('.fab'), clicarTexto('Adicionar item do mercado'), clicar('.pick')],
  },

  // RF008 — registrar consumo no dia, ver a composição do registro e navegar por data.
  { arquivo: '13-diario-adicionar', rota: '/diario', passos: [clicar('.fab')] },
  { arquivo: '14-diario-registro', rota: '/diario', passos: [clicar('.linha')] },
  { arquivo: '15-diario-calendario', rota: '/diario', passos: [clicar('.rng')] },

  // RF009 e conta.
  { arquivo: '16-perfil-editar', rota: '/perfil', passos: [clicarTexto('Editar perfil')] },
  { arquivo: '17-perfil-meta', rota: '/perfil', passos: [clicarTexto('Meta de orçamento')] },
  { arquivo: '18-perfil-senha', rota: '/perfil', passos: [clicarTexto('Alterar a senha')] },
];

/**
 * Marca o convite de instalar o app como já dispensado.
 *
 * A faixa é legítima em uso real, mas numa figura de documento ela cobre o topo da tela e
 * rouba a atenção do que a imagem deveria mostrar. `instalar-app.ts` guarda a recusa por
 * conta, então cobre-se a chave com o id do token e a versão sem id (telas deslogadas).
 */
function dispensarConviteDeInstalar(token) {
  const prefixo = 'mealmath:instalar-dispensado';
  return `(() => {
    localStorage.setItem('${prefixo}', '1');
    try {
      const sub = JSON.parse(atob(${JSON.stringify(token)}.split('.')[1])).sub;
      localStorage.setItem('${prefixo}:' + sub, '1');
    } catch { /* telas públicas não têm token válido; a chave sem id já basta */ }
    return true;
  })()`;
}

let proximoId = 0;

/** Cliente CDP mínimo: `send` resolve na resposta do comando correspondente. */
function conectar(url) {
  const socket = new WebSocket(url);
  const pendentes = new Map();

  socket.addEventListener('message', (evento) => {
    const msg = JSON.parse(evento.data);
    if (msg.id === undefined) return; // evento do protocolo, não resposta
    const pendente = pendentes.get(msg.id);
    if (!pendente) return;
    pendentes.delete(msg.id);
    msg.error ? pendente.rejeitar(new Error(msg.error.message)) : pendente.resolver(msg.result);
  });

  const pronto = new Promise((resolver, rejeitar) => {
    socket.addEventListener('open', resolver, { once: true });
    socket.addEventListener('error', () => rejeitar(new Error('falha ao abrir o CDP')), {
      once: true,
    });
  });

  return {
    pronto,
    /** `sessionId` vai no envelope, não em params — é o modo flatten do CDP. */
    send(method, params = {}, sessionId) {
      const id = ++proximoId;
      socket.send(JSON.stringify({ id, method, params, ...(sessionId && { sessionId }) }));
      return new Promise((resolver, rejeitar) => pendentes.set(id, { resolver, rejeitar }));
    },
    fechar: () => socket.close(),
  };
}

/** Espera um endereço responder — o Chrome leva um instante para abrir a porta de debug. */
async function aguardarEndereco(url, tentativas = 60) {
  for (let i = 0; i < tentativas; i++) {
    try {
      const resposta = await fetch(url);
      if (resposta.ok) return resposta;
    } catch {
      // ainda subindo
    }
    await esperar(250);
  }
  throw new Error(`sem resposta de ${url}`);
}

async function main() {
  const destino = fileURLToPath(SAIDA);
  await mkdir(destino, { recursive: true });

  await aguardarEndereco(APP, 8).catch(() => {
    throw new Error(`${APP} não responde — suba o ng serve antes.`);
  });

  const chrome = spawn('google-chrome', [
    '--headless=new',
    `--remote-debugging-port=${PORTA_CDP}`,
    '--hide-scrollbars',
    '--no-first-run',
    '--no-default-browser-check',
    '--user-data-dir=/tmp/chrome-capturas-mealmath',
    'about:blank',
  ]);
  chrome.on('error', (erro) => console.error('Chrome não subiu:', erro.message));

  const cdp = conectar(
    (await (await aguardarEndereco(`http://127.0.0.1:${PORTA_CDP}/json/version`)).json())
      .webSocketDebuggerUrl
  );

  try {
    await cdp.pronto;

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });

    /** Comando já amarrado à aba criada. */
    const cmd = (method, params) => cdp.send(method, params, sessionId);

    const avaliar = async (expressao) => {
      const { result, exceptionDetails } = await cmd('Runtime.evaluate', {
        expression: expressao,
        awaitPromise: true,
        returnByValue: true,
      });
      if (exceptionDetails) {
        throw new Error(exceptionDetails.exception?.description ?? exceptionDetails.text);
      }
      return result.value;
    };

    await cmd('Page.enable');
    await cmd('Runtime.enable');
    // deviceScaleFactor 2 = imagem em 2x, para a figura não sair borrada no documento.
    await cmd('Emulation.setDeviceMetricsOverride', {
      ...VIEWPORT,
      deviceScaleFactor: 2,
      mobile: true,
    });

    const irPara = async (rota) => {
      await cmd('Page.navigate', { url: `${APP}${rota}` });
      // Espera o Angular pintar e a chamada da API voltar: o marcador é sumir o "Carregando".
      for (let i = 0; i < 40; i++) {
        await esperar(250);
        const pronto = await avaliar(`(() => {
          const app = document.querySelector('main, app-root');
          if (!app || app.textContent.trim().length === 0) return false;
          return !/Carregando/i.test(app.textContent);
        })()`).catch(() => false);
        if (pronto) break;
      }
      await esperar(400); // respiro para transição/animação assentar
    };

    // Autentica pela própria API. O token fica aqui no Node porque as telas públicas
    // limpam o localStorage, e sem uma cópia não haveria como repô-lo depois.
    await irPara('/login');
    const token = await avaliar(`
      fetch('${API}/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(${JSON.stringify(CONTA)})
      })
      .then(r => r.ok ? r.json() : Promise.reject(new Error('login ' + r.status)))
      .then(d => d.token)
    `);
    if (!token) throw new Error('não consegui autenticar a conta de exemplo');

    for (const tela of TELAS) {
      // O guard de rota decide pela presença do token: sem ele, /dashboard cai no login.
      await avaliar(
        tela.sessao === false
          ? `localStorage.removeItem('${CHAVE_TOKEN}')`
          : `localStorage.setItem('${CHAVE_TOKEN}', ${JSON.stringify(token)})`
      );
      await avaliar(dispensarConviteDeInstalar(token));

      await irPara(tela.rota);

      // Cada passo abre uma etapa do fluxo; o respiro deixa a bottom sheet terminar de subir
      // antes do próximo clique, senão o elemento seguinte ainda não está no DOM.
      for (const passo of tela.passos ?? []) {
        await avaliar(passo);
        await esperar(500);
      }

      // Condição de parada da própria tela, quando "não estar carregando" não basta.
      if (tela.aguardar) {
        let pronto = false;
        for (let i = 0; i < 40 && !pronto; i++) {
          pronto = Boolean(await avaliar(tela.aguardar).catch(() => false));
          if (!pronto) await esperar(250);
        }
        if (!pronto) throw new Error(`${tela.arquivo}: condição de espera não cumprida`);
        await esperar(300);
      }

      const { data } = await cmd('Page.captureScreenshot', { format: 'png' });
      const caminho = new URL(`${tela.arquivo}.png`, SAIDA);
      await writeFile(caminho, Buffer.from(data, 'base64'));

      const url = await avaliar('location.pathname');
      console.log(`${tela.arquivo}.png  ←  ${url}`);
    }

    console.log(`\n${TELAS.length} telas em ${destino}`);
  } finally {
    cdp.fechar();
    chrome.kill();
  }
}

await main();
