import { UnidadeMedida } from '../../core/dominio/unidade';

/** Interfaces da rota /refeicoes. Espelham os records do backend. */

export interface ItemRefeicaoRequest {
  itemMercadoId: number;
  quantidadeConsumida: number;
  /** Precisa ser da mesma grandeza da embalagem do item. */
  unidade: UnidadeMedida;
}

export interface RefeicaoRequest {
  titulo: string;
  icone: string;
  /** No PUT essa lista substitui tudo: o que nao vier aqui e apagado da refeicao. */
  itens: ItemRefeicaoRequest[];
}

export interface ItemRefeicaoResponse {
  /** Id da linha da refeicao, e nao do item de mercado. */
  id: number;
  itemMercadoId: number;
  /** Nome atual do item de mercado. */
  nome: string;
  quantidadeConsumida: number;
  unidade: UnidadeMedida;
  /** Custo dessa porcao, sem arredondar. */
  custo: number;
  /** False quer dizer item de mercado desativado. Ele continua vinculado e continua custando. */
  itemAtivo: boolean;
}

export interface RefeicaoResponse {
  id: number;
  titulo: string;
  /** Icone escolhido no formulario. Pode ser null em refeicao criada sem icone. */
  icone: string | null;
  itens: ItemRefeicaoResponse[];
  /** Soma do custo dos itens, sem arredondar no meio do caminho. */
  custoTotal: number;
}
