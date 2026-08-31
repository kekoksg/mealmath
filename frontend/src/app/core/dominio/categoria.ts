/**
 * Categorias de alimento, com os mesmos rotulos que a API devolve.
 *
 * O icone e escolha da interface, porque a API nao guarda icone de item. A cor vem do seletor
 * [data-cat] no SCSS, entao nenhum codigo de cor aparece no TypeScript.
 */

/** `Categoria` do backend. */
export type Categoria = 'PROTEINA' | 'HORTIFRUTI' | 'CARBOIDRATO' | 'LATICINIO' | 'OUTROS';

interface DefinicaoCategoria {
  readonly valor: Categoria;
  readonly rotulo: string;
  /** Nome do icone no mapa de shared/icone, nao o desenho em si. */
  readonly icone: string;
}

/** Ordem que aparece no seletor. OUTROS fica por ultimo por ser o padrao da API. */
export const CATEGORIAS: readonly DefinicaoCategoria[] = [
  { valor: 'PROTEINA', rotulo: 'Proteína', icone: 'cat-proteina' },
  { valor: 'HORTIFRUTI', rotulo: 'Hortifruti', icone: 'cat-hortifruti' },
  { valor: 'CARBOIDRATO', rotulo: 'Carboidrato', icone: 'cat-carboidrato' },
  { valor: 'LATICINIO', rotulo: 'Laticínio', icone: 'cat-laticinio' },
  { valor: 'OUTROS', rotulo: 'Outros', icone: 'cat-outros' },
];

const POR_VALOR = new Map(CATEGORIAS.map((categoria) => [categoria.valor, categoria]));

const PADRAO = CATEGORIAS[CATEGORIAS.length - 1];

export function iconeDaCategoria(categoria: Categoria): string {
  return (POR_VALOR.get(categoria) ?? PADRAO).icone;
}

export function rotuloDaCategoria(categoria: Categoria): string {
  return (POR_VALOR.get(categoria) ?? PADRAO).rotulo;
}
