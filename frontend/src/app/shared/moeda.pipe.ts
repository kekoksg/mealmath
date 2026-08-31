import { Pipe, PipeTransform } from '@angular/core';

const FORMATO = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * Formata valor em dinheiro como R$ 0,00. E aqui, e so aqui, que o arredondamento acontece.
 *
 * Intl em vez do CurrencyPipe porque o Angular so ja vem com o locale en-US. Sem registrar
 * o pt-BR, o CurrencyPipe mostra R$1,234.50.
 */
export function formatarMoeda(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return '—';
  }
  return `R$ ${FORMATO.format(valor)}`;
}

@Pipe({ name: 'moeda' })
export class MoedaPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return formatarMoeda(valor);
  }
}
