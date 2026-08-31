/** Interfaces da rota /perfil. Espelham os records do backend. */

export interface PerfilRequest {
  nome: string;
  email: string;
}

export interface AlterarSenhaRequest {
  senhaAtual: string;
  novaSenha: string;
}
