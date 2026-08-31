/** Interfaces das rotas /auth. Espelham os records do backend. */

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface CadastroRequest {
  nome: string;
  email: string;
  senha: string;
}

export interface UsuarioResponse {
  id: number;
  nome: string;
  email: string;
    /**
     * O "Membro desde" do perfil. Opcional porque um token emitido antes desse campo existir
     * continua valendo ate expirar.
     */
  criadoEm?: string;
}

export interface TokenResponse {
  token: string;
  /** Sempre vem "Bearer". */
  tipo: string;
  expiraEmSegundos: number;
  usuario: UsuarioResponse;
}
