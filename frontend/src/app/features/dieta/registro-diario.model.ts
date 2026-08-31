import { DataIso } from '../../core/dominio/data';
import { UnidadeMedida } from '../../core/dominio/unidade';

/** Interfaces da rota /registros-diarios. Espelham os records do backend. */

export interface RegistroDiarioRequest {
  data: DataIso;
  /** Refeicao da biblioteca que vai para o dia. O backend grava uma copia dela. */
  refeicaoId: number;
}

/** Ajuste que vale so para a linha daquele dia. */
export interface ItemRegistroQuantidadeRequest {
  quantidadeConsumida: number;
  /** Precisa ser da mesma grandeza da embalagem, quando o item ainda tem vinculo. */
  unidade: UnidadeMedida;
}

export interface DuplicarDiaAnteriorRequest {
  /** Dia de destino. A origem e sempre o dia anterior a ele. */
  data: DataIso;
}

export interface ItemRegistroResponse {
  /** Id da linha do registro. E ele que o ajuste de quantidade usa. */
  id: number;
  /** Vem null quando o vinculo com o item de mercado se perdeu. */
  itemMercadoId: number | null;
  /** Nome copiado na hora em que o registro foi criado. */
  nome: string;
  quantidadeConsumida: number;
  unidade: UnidadeMedida;
  /** Vem null, e nunca 0, quando nao tem preco vinculado. */
  custo: number | null;
  itemAtivo: boolean;
}

export interface RegistroDiarioResponse {
  id: number;
  data: DataIso;
  /** Copia do titulo do modelo. Renomear na biblioteca nao muda o que ja passou. */
  titulo: string;
  icone: string | null;
  /** So serve de rastreio. Fica null se o modelo foi apagado, sem afetar o registro. */
  refeicaoOrigemId: number | null;
  itens: ItemRegistroResponse[];
  /** Soma so dos itens que tem preco, sem arredondar no meio do caminho. */
  custoTotal: number;
  /** Nomes que ficaram de fora do total. A tela precisa mostrar, senao o valor engana. */
  itensSemPreco: string[];
}
