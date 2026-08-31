/**
 * Unidades de medida e a conversao para a unidade base.
 *
 * O calculo que vale e o do backend, em BigDecimal. O daqui usa number e serve so para a previa
 * enquanto o usuario digita; nunca e enviado para a API.
 */

/** Espelha o enum UnidadeMedida do backend. */
export type UnidadeMedida = 'KG' | 'G' | 'L' | 'ML' | 'UN';

/** Grandeza fisica. So da para converter dentro da mesma: massa com massa, volume com volume. */
export type Grandeza = 'MASSA' | 'VOLUME' | 'CONTAGEM';

/** Espelha o campo unidadeBase das respostas da API. */
export type UnidadeBase = 'g' | 'mL' | 'un';

interface DefinicaoUnidade {
  readonly valor: UnidadeMedida;
  /** Rotulo curto, do jeito que aparece na embalagem. */
  readonly rotulo: string;
  readonly grandeza: Grandeza;
  /** Quantas unidades base cabem em 1 dessa unidade. */
  readonly fator: number;
}

const DEFINICOES: Readonly<Record<UnidadeMedida, DefinicaoUnidade>> = {
  KG: { valor: 'KG', rotulo: 'kg', grandeza: 'MASSA', fator: 1000 },
  G: { valor: 'G', rotulo: 'g', grandeza: 'MASSA', fator: 1 },
  L: { valor: 'L', rotulo: 'L', grandeza: 'VOLUME', fator: 1000 },
  ML: { valor: 'ML', rotulo: 'mL', grandeza: 'VOLUME', fator: 1 },
  UN: { valor: 'UN', rotulo: 'un', grandeza: 'CONTAGEM', fator: 1 },
};

const BASES: Readonly<Record<Grandeza, UnidadeBase>> = {
  MASSA: 'g',
  VOLUME: 'mL',
  CONTAGEM: 'un',
};

/** Ordem que aparece no seletor, a mesma do prototipo. */
export const UNIDADES: readonly DefinicaoUnidade[] = [
  DEFINICOES.KG,
  DEFINICOES.G,
  DEFINICOES.L,
  DEFINICOES.ML,
  DEFINICOES.UN,
];

export function rotuloDaUnidade(unidade: UnidadeMedida): string {
  return DEFINICOES[unidade].rotulo;
}

export function grandezaDa(unidade: UnidadeMedida): Grandeza {
  return DEFINICOES[unidade].grandeza;
}

export function unidadeBaseDe(unidade: UnidadeMedida): UnidadeBase {
  return BASES[grandezaDa(unidade)];
}

/** Converte para a unidade base. 1 kg e 1000 g tem que dar o mesmo numero. */
export function paraBase(quantidade: number, unidade: UnidadeMedida): number {
  return quantidade * DEFINICOES[unidade].fator;
}

/**
 * Define como o custo unitario aparece na tela. Em g e mL o custo de 1 unidade base e pequeno
 * demais para 2 casas: R$ 0,0189/g viraria R$ 0,02/g e o valor sairia distorcido. Por isso a
 * lista mostra o custo de 100 unidades base.
 */
export interface CustoDeReferencia {
  readonly valor: number;
  /** Sufixo pronto para exibicao: `/100g`, `/100mL` ou `/un`. */
  readonly rotulo: string;
}

export function custoDeReferencia(
  custoUnitario: number,
  base: UnidadeBase | string
): CustoDeReferencia {
  if (base === 'un') {
    return { valor: custoUnitario, rotulo: '/un' };
  }
  return { valor: custoUnitario * 100, rotulo: `/100${base}` };
}
