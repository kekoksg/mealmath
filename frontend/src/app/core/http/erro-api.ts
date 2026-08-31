import { HttpErrorResponse } from '@angular/common/http';

/** Formato do erro que a API devolve. E o mesmo record ErroResponse do backend. */
export interface ErroResponse {
  mensagem: string;
  /** So vem quando o erro e de validacao de formulario (400). */
  campos?: Record<string, string>;
}

/**
 * Devolve a mensagem pronta para mostrar na tela. A API ja manda o texto em pt-BR, e o
 * parametro padrao cobre os casos que vem sem corpo, como o 401 do Spring Security.
 */
export function mensagemDeErro(falha: HttpErrorResponse, padrao: string): string {
  // Status 0 quer dizer que a requisicao nem chegou na API: sem internet, CORS ou servidor fora.
  if (falha.status === 0) {
    return 'Não foi possível falar com o servidor. Verifique sua conexão e tente de novo.';
  }

  const corpo = falha.error as ErroResponse | null;
  return corpo?.mensagem?.trim() ? corpo.mensagem : padrao;
}

/** Erros campo a campo que vem em um 400 de validacao. Devolve vazio quando nao tem. */
export function camposComErro(falha: HttpErrorResponse): Record<string, string> {
  const corpo = falha.error as ErroResponse | null;
  return corpo?.campos ?? {};
}
