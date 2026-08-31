/**
 * Mede o contraste dos pares de cor do app contra a WCAG 2.1 AA.
 *
 * Le os valores de src/styles/_tokens.scss e testa cada cor no papel em que ela e
 * realmente usada — o piso muda conforme o papel:
 *
 *   4,5:1  texto abaixo de 18,66px em negrito ou 24px normal (criterio 1.4.3)
 *   3,0:1  texto grande, componente de interface e objeto grafico (1.4.3 e 1.4.11)
 *
 * A lista abaixo e escrita a mao de proposito. Varrer o SCSS atras de pares acharia
 * combinacao que nunca acontece na tela e, pior, deixaria passar as que dependem de
 * composicao (a tag de variacao, que e um rgba por cima do verde).
 *
 * Sai com codigo 1 se algum par reprovar, entao serve em CI.
 *
 *   npm run contraste
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');
const fonte = readFileSync(join(raiz, 'src/styles/_tokens.scss'), 'utf8');

const HEX = new Map([...fonte.matchAll(/--([\w-]+):\s*(#[0-9A-Fa-f]{6})/g)].map((m) => [m[1], m[2]]));
const RGBA = new Map([...fonte.matchAll(/--([\w-]+):\s*(rgba\([^)]+\))/g)].map((m) => [m[1], m[2]]));

const paraRgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));

/** Compoe um token rgba() sobre um fundo opaco, que e como o navegador desenha. */
function sobre(nome, fundo) {
  const [, r, g, b, a] = RGBA.get(nome).match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/);
  return [r, g, b].map((c, i) => Math.round(Number(c) * Number(a) + fundo[i] * (1 - Number(a))));
}

const luminancia = (rgb) =>
  rgb
    .map((c) => {
      const v = c / 255;
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
    })
    .reduce((acc, v, i) => acc + v * [0.2126, 0.7152, 0.0722][i], 0);

function razao(frente, fundo) {
  const [a, b] = [luminancia(frente), luminancia(fundo)].sort((x, y) => y - x);
  return (a + 0.05) / (b + 0.05);
}

const t = (nome) => paraRgb(HEX.get(nome));

const SUP = t('superficie');
const VERDE = t('green');
const VERMELHO = t('red');
const A_SOFT = t('amber-soft');
const G_SOFT = t('green-soft');
const TRILHO = t('line');
const BRANCO = t('on-cor');
const TAG = sobre('on-green-soft', VERDE);

/** [descricao do papel, frente, fundo, piso] */
const PARES = [
  ['titulo de tela (--ink, 27px/800)', t('ink'), SUP, 3],
  ['corpo (--text)', t('text'), SUP, 4.5],
  ['rotulo secundario (--muted)', t('muted'), SUP, 4.5],
  ['legenda minima e placeholder (--faint)', t('faint'), SUP, 4.5],
  ['valor em dinheiro (--green, 15,5px/700)', t('green'), SUP, 4.5],
  ['erro e acima da meta (--red)', t('red'), SUP, 4.5],
  ['aviso de item sem preco (--amber-ink)', t('amber-ink'), SUP, 4.5],

  ['valor do topo (--on-cor sobre verde)', BRANCO, VERDE, 3],
  ['rodape do topo (--on-green sobre verde)', sobre('on-green', VERDE), VERDE, 4.5],
  ['tag de variacao neutra (branco na tag)', BRANCO, TAG, 4.5],
  ['botao de apagar (--on-cor sobre vermelho)', BRANCO, VERMELHO, 4.5],

  ['titulo do alerta (--ink sobre soft)', t('ink'), A_SOFT, 4.5],
  ['texto do alerta (--amber-ink sobre soft)', t('amber-ink'), A_SOFT, 4.5],
  ['alta de preco (--amber-forte sobre soft)', t('amber-forte'), A_SOFT, 4.5],
  ['tag de variacao em alta (--amber-ink sobre soft)', t('amber-ink'), A_SOFT, 4.5],
  ['icone da lista (--green sobre green-soft)', t('green'), G_SOFT, 3],

  ['alca de arrastar (--grip)', t('grip'), SUP, 3],
  ['tres pontinhos (--kebab)', t('kebab'), SUP, 3],
  ['barra da meta dentro (--green no trilho)', t('green'), TRILHO, 3],
  ['barra da meta em risco (--amber no trilho)', t('amber'), TRILHO, 3],
  ['barra da meta estourada (--red no trilho)', t('red'), TRILHO, 3],

  ...[
    ['prot', 'proteina'],
    ['carb', 'carboidrato'],
    ['horti', 'hortifruti'],
    ['lat', 'laticinio'],
    ['outro', 'outros'],
  ].map(([cat, nome]) => [`fatia do grafico: ${nome}`, t(`cat-${cat}`), SUP, 3]),
];

const larg = Math.max(...PARES.map(([rotulo]) => rotulo.length));
const falhas = [];

console.log(`${'par'.padEnd(larg)}  razao  piso`);
console.log('-'.repeat(larg + 16));
for (const [rotulo, frente, fundo, piso] of PARES) {
  const r = razao(frente, fundo);
  if (r < piso) falhas.push([rotulo, r, piso]);
  console.log(
    `${rotulo.padEnd(larg)}  ${r.toFixed(2).padStart(5)}  ${piso.toFixed(1)}  ${r >= piso ? 'ok' : 'REPROVA'}`,
  );
}

console.log(`\n${PARES.length - falhas.length} de ${PARES.length} pares passam.`);
for (const [rotulo, r, piso] of falhas) {
  console.log(`  REPROVA ${rotulo}: ${r.toFixed(2)}:1, piso ${piso.toFixed(1)}:1`);
}
process.exit(falhas.length ? 1 : 0);
