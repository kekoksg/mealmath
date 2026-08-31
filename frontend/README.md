# Frontend

SPA em Angular standalone com SSR e service worker, desenhada para 390 px de largura e escalando
para cima. Consome a API em `:8082`; a fonte da verdade é sempre ela, e o `localStorage` guarda o
token e nada mais.

Este README cobre só o front. Para instalar o projeto inteiro, incluindo banco e backend, veja o
[COMO-USAR.md](../COMO-USAR.md) na raiz.

## Rodando

```bash
npm install
npm start
```

Sobe o dev server em <http://localhost:4200> com recarga automática. O backend precisa estar de pé
em `:8082` — em desenvolvimento o front fala direto com essa porta, sem proxy.

Para expor na rede local e abrir no celular:

```bash
npx ng serve --host 0.0.0.0
```

Nesse caso a API também precisa estar acessível na rede, porque a URL é montada a partir do
hostname do navegador.

## Build

```bash
npm run build
```

Gera o bundle e o servidor SSR em `dist/`. Para servir o resultado:

```bash
node dist/frontend/server/server.mjs
```

O SSR escuta na `:4000` (ou na porta em `PORT`) e encaminha `/api` para o backend, de modo que o
navegador conversa com uma origem só.

## Testes

```bash
npx ng test --watch=false
```

São 121 testes cobrindo serviços, validadores e componentes de tela.

```bash
npm run contraste
```

Mede 26 pares de cor contra a WCAG 2.1 AA, cada um no papel em que a cor é usada: 4,5:1 para
texto, 3:1 para componente de interface e objeto gráfico. Lê os valores de `src/styles/_tokens.scss`
e falha se algum par reprovar.

## Estrutura

```
src/app/
├── core/        auth, guard, interceptor, domínio compartilhado
├── features/    dashboard · dieta · mercado · perfil · login · cadastro
├── layout/      shell + bottom nav
└── shared/      bottom sheet, ícones, pipe de moeda
```

Os ícones são extraídos em tempo de build por `scripts/gerar-icones.mjs` e as capturas de tela da
documentação são geradas por `scripts/capturar-telas.mjs`. Nada é baixado em runtime.

## Créditos

- **Solar Icon Set** — [480 Design](https://www.figma.com/community/file/1166831539721848736),
  sob [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- **Plus Jakarta Sans** — Tokotype, sob SIL Open Font License 1.1. Servida por `public/fonts`,
  também sem chamada externa.
