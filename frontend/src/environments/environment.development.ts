// O window nao existe quando o servidor avalia esse arquivo no SSR, so no navegador, que e
// onde a chamada para a API acontece de verdade.
const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';

export const environment = {
  producao: false,
  // A API sobe na porta 8082 e as rotas ficam na raiz, sem prefixo.
  // Usa o mesmo host que serviu o front: localhost no PC e o IP da rede local no celular.
  // Com 'localhost' fixo, o celular apontaria para si mesmo e nao para esse backend.
  apiUrl: `http://${host}:8082`,
};
