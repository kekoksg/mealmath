/**
 * Extrai do Solar Icon Set apenas os ícones que a aplicação usa e grava um mapa TS.
 *
 * Por que gerar em vez de importar em runtime: o pacote tem 7.6 mil ícones e a aplicação
 * usa algumas dezenas. Embutir só o necessário mantém o bundle pequeno e, sobretudo, sem
 * nenhuma requisição externa — a mesma razão pela qual a fonte foi trazida para dentro
 * do projeto.
 *
 * Rode depois de acrescentar um nome novo em USADOS:
 *   node scripts/gerar-icones.mjs
 *
 * Solar Icon Set — 480 Design, CC BY 4.0 (creativecommons.org/licenses/by/4.0/).
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { PROPRIOS } from './icones-proprios.mjs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = resolve(AQUI, '..');

/**
 * Nome interno -> ícone do Solar (estilo linear).
 *
 * O nome interno descreve o **papel** na interface, não o desenho: se um dia o ícone de
 * "meta" deixar de ser um alvo, troca-se aqui e nenhuma tela precisa mudar.
 */
const USADOS = {
  // ---------- Navegação ----------
  inicio: 'home-smile',
  diario: 'calendar',
  refeicao: 'chef-hat',
  mercado: 'cart-large-2',

  // ---------- Cabeçalhos e ações ----------
  perfil: 'user-circle',
  meta: 'target',
  composicao: 'pie-chart-2',
  impacto: 'cup-star',
  alerta: 'bell',
  editar: 'pen-2',
  excluir: 'trash-bin-trash',
  buscar: 'magnifer',
  fechar: 'close-circle',
  mais: 'add-circle',
  opcoes: 'menu-dots',
  arrastar: 'hamburger-menu',
  anterior: 'alt-arrow-left',
  proximo: 'alt-arrow-right',
  repetir: 'refresh',
  sair: 'logout-2',
  membro: 'calendar-date',
  olho: 'eye',
  'olho-fechado': 'eye-closed',
  marca: 'leaf',

  // ---------- Estados ----------
  'sem-rede': 'wi-fi-router-round',
  'sem-registro': 'plate',
  'sem-preco': 'tag-price',
  aviso: 'danger-triangle',
  alta: 'graph-up',

  // Comida não vem do Solar: os desenhos próprios estão em icones-proprios.mjs.
  // Aqui ficam só os do conjunto, para o que ele cobre bem.
  'cat-outros': 'cart-large-2',
};

const colecao = JSON.parse(
  readFileSync(resolve(RAIZ, 'node_modules/@iconify-json/solar/icons.json'), 'utf8')
);

const larguraPadrao = colecao.width ?? 24;
const alturaPadrao = colecao.height ?? 24;

/** Iconify guarda alguns nomes como apelido de outro ícone; resolve até o desenho. */
function desenhoDe(nome) {
  let atual = nome;
  for (let salto = 0; salto < 5; salto++) {
    if (colecao.icons[atual]) {
      return colecao.icons[atual];
    }
    const apelido = colecao.aliases?.[atual];
    if (!apelido) {
      return null;
    }
    atual = apelido.parent;
  }
  return null;
}

const linhas = [];
const faltando = [];

for (const [interno, solar] of Object.entries(USADOS)) {
  const nomeSolar = `${solar}-linear`;
  const desenho = desenhoDe(nomeSolar);
  if (!desenho) {
    faltando.push(`${interno} (${nomeSolar})`);
    continue;
  }
  const largura = desenho.width ?? larguraPadrao;
  const altura = desenho.height ?? alturaPadrao;
  const corpo = desenho.body.replace(/\s+/g, ' ').trim();
  linhas.push(`  '${interno}': { caixa: '0 0 ${largura} ${altura}', corpo: '${corpo}' },`);
}

// Desenhos próprios entram depois: comida não existe no Solar, e é aqui que ela chega.
for (const [nome, corpo] of Object.entries(PROPRIOS)) {
  linhas.push(`  '${nome}': { caixa: '0 0 24 24', corpo: '${corpo.replace(/'/g, "\\'")}' },`);
}

if (faltando.length) {
  console.error('Ícones não encontrados no Solar:\n  ' + faltando.join('\n  '));
  process.exit(1);
}

const saida = `// ARQUIVO GERADO — não edite à mão.
// Rode \`node scripts/gerar-icones.mjs\` depois de mexer em USADOS lá.
//
// Solar Icon Set por 480 Design, sob CC BY 4.0.
// https://creativecommons.org/licenses/by/4.0/

/** Um ícone pronto para virar <svg>: a caixa de coordenadas e o desenho. */
export interface Icone {
  readonly caixa: string;
  readonly corpo: string;
}

export const ICONES: Readonly<Record<string, Icone>> = {
${linhas.join('\n')}
};

/** Nome válido de ícone. Serve para distinguir de um emoji legado gravado no banco. */
export type NomeDeIcone = keyof typeof ICONES;
`;

const destino = resolve(RAIZ, 'src/app/shared/icone');
mkdirSync(destino, { recursive: true });
writeFileSync(resolve(destino, 'icones.ts'), saida);

console.log(`${linhas.length} ícones gravados em src/app/shared/icone/icones.ts`);
