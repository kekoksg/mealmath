import { Categoria } from '../../core/dominio/categoria';
import { UnidadeBase, UnidadeMedida } from '../../core/dominio/unidade';

/** Interfaces da rota /itens-mercado. Espelham os records do backend. */

export interface ItemMercadoRequest {
  nome: string;
  preco: number;
  quantidadeEmbalagem: number;
  unidade: UnidadeMedida;
  /** Se nao vier, o backend usa OUTROS. O formulario sempre manda alguma. */
  categoria: Categoria;
}

export interface ItemMercadoResponse {
  id: number;
  nome: string;
  /** Preco da embalagem inteira. */
  preco: number;
  quantidadeEmbalagem: number;
  unidade: UnidadeMedida;
  /** Custo de 1 unidade base, sem arredondar. Mostrar com 2 casas distorce o valor. */
  custoUnitario: number;
  unidadeBase: UnidadeBase;
  categoria: Categoria;
  /** False quer dizer desativado. A listagem so traz os ativos. */
  ativo: boolean;
  /** Vem como texto ISO-8601. */
  atualizadoEm: string;
}

export interface HistoricoPrecoResponse {
  id: number;
  preco: number;
  quantidadeEmbalagem: number;
  unidade: UnidadeMedida;
  custoUnitario: number;
  unidadeBase: UnidadeBase;
  substituidoEm: string;
}
