/**
 * Tira o acento e a caixa para a busca funcionar: o teclado do celular nem sempre acentua, e sem
 * isso "proteina" nao acha "Proteina".
 */
export function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}
