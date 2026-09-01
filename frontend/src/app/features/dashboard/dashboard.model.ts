import { Categoria } from '../../core/dominio/categoria';
import { DataIso } from '../../core/dominio/data';
import { UnidadeBase } from '../../core/dominio/unidade';

/** Interfaces das rotas /dashboard e /meta-orcamento. Espelham os records do backend. */

/** Janela consolidada, em dias corridos ate hoje. Nao e o mes do calendario. */
export type PeriodoDashboard = 'DIA' | 'SEMANA' | 'MES';

/** Periodo do limite de gasto cadastrado (RF009). E independente do periodo da janela. */
export type PeriodoMeta = 'SEMANAL' | 'MENSAL';

export interface ComparativoPeriodoResponse {
  inicio: DataIso;
  fim: DataIso;
  custoTotal: number;
  variacaoPercentual: number;
}

export interface CompletudeDiarioResponse {
  diasComRegistro: number;
  /** Tamanho da janela. E o "M" do texto "N de M dias". */
  totalDeDias: number;
}

export interface ProgressoMetaResponse {
  valor: number;
  periodo: PeriodoMeta;
  /** Meta rateada para a janela: R$ 450,00 no mes, olhando 7 dias, vale R$ 105,00. */
  valorNoPeriodo: number;
  /** Vem com 2 casas e nao para em 100, porque estourar a meta em 130% e informacao util. */
  percentualConsumido: number;
  saldo: number;
  acimaDaMeta: boolean;
}

export interface ComposicaoCategoriaResponse {
  categoria: Categoria;
  rotulo: string;
  custo: number;
  percentual: number;
}

export interface ItemImpactoResponse {
  itemMercadoId: number;
  nome: string;
  categoria: Categoria;
  custo: number;
  percentual: number;
}

/**
 * Item que ficou mais caro (RF006 e RF007). A variacao e medida no custo unitario e nao no
 * preco da etiqueta, porque os dois so batem quando a embalagem continua do mesmo tamanho.
 */
export interface AltaPrecoResponse {
  itemMercadoId: number;
  nome: string;
  precoAnterior: number;
  precoAtual: number;
  custoUnitarioAnterior: number;
  custoUnitarioAtual: number;
  unidadeBase: UnidadeBase;
  variacaoPercentual: number;
  alteradoEm: string;
}

/** Espelha o DashboardResponse do backend (RF006). */
export interface DashboardResponse {
  periodo: PeriodoDashboard;
  /** Janela que foi consolidada de fato, com as duas pontas incluidas. */
  inicio: DataIso;
  fim: DataIso;
  custoTotal: number;
  /** Dividido pelos dias da janela, e nao pelos dias que tem registro. */
  custoMedioPorDia: number;
  comparativo: ComparativoPeriodoResponse | null;
  completude: CompletudeDiarioResponse;
  meta: ProgressoMetaResponse | null;
  composicaoPorCategoria: ComposicaoCategoriaResponse[];
  itensMaiorImpacto: ItemImpactoResponse[];
  altasDePreco: AltaPrecoResponse[];
  /** Nomes que ficaram de fora de todos os totais acima. Nunca sao contados como R$ 0,00. */
  itensSemPreco: string[];
}

export interface MetaOrcamentoRequest {
  valor: number;
  periodo: PeriodoMeta;
}

export interface MetaOrcamentoResponse {
  id: number;
  valor: number;
  periodo: PeriodoMeta;
  /** Vem como texto ISO-8601. */
  atualizadoEm: string;
}
