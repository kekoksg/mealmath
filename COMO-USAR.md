<div align="center">

# 🥗 MealMath — guia de uso

**Do `git clone` ao primeiro custo calculado.**

Este é o manual prático: instalar, subir, usar e desenvolver.
Para o contexto do projeto, o problema que ele resolve e as decisões de arquitetura,
veja o **[README](README.md)**.

</div>

---

## Sumário

1. [O que o MealMath faz](#1-o-que-o-mealmath-faz)
2. [Requisitos](#2-requisitos)
3. [Instalação passo a passo](#3-instalação-passo-a-passo)
4. [Primeiro uso — do zero ao dashboard](#4-primeiro-uso--do-zero-ao-dashboard)
5. [Instalar no celular](#5-instalar-no-celular)
6. [Modo desenvolvimento](#6-modo-desenvolvimento)
7. [Testes](#7-testes)
8. [Configuração](#8-configuração)
9. [Deu problema?](#9-deu-problema)
10. [Estrutura do código](#10-estrutura-do-código)

---

## 1. O que o MealMath faz

Ele te dá controle sobre o gasto com alimentação **no dia**, e não no extrato do fim do mês:
quanto custou o dia, a semana e o mês; onde o dinheiro está indo por categoria; quais itens
pesam mais; quais subiram de preço; e se você está dentro da meta.

Para isso você cadastra o que comprou no mercado (**preço + embalagem**), monta suas refeições
com esses itens (**quantidade consumida**) e registra o que comeu a cada dia. O rateio
fracionado — a conta que inviabiliza o controle manual — fica por conta do sistema:

> *Frango a R$ 18,90 o quilo. Comi 150 g. **Custou R$ 2,84.***

| | |
|---|---|
| 🛒 **Mercado** | itens com preço, embalagem e categoria — o custo unitário aparece na hora |
| 🍽️ **Refeições** | modelos reutilizáveis ("Almoço", "Café da manhã") com quantidades padrão |
| 📅 **Diário** | o que foi consumido em cada data, com cópia própria dos itens |
| 📊 **Visão Geral** | custo por dia/semana/mês, comparação com o período anterior, meta e composição |
| 📈 **Histórico** | todo preço trocado é empilhado, nunca sobrescrito |
| 📱 **Instalável** | roda como app no celular (PWA), com SSR |

---

## 2. Requisitos

| Software | Versão | Verificar |
|---|---|---|
| JDK | 17 ou superior | `java -version` |
| Node.js | 20 ou superior | `node -v` |
| PostgreSQL | 14 ou superior | `psql --version` |

O Maven **não** precisa estar instalado — o projeto usa o wrapper (`./mvnw`).

<details>
<summary>Instalar no Ubuntu / Pop!_OS / Debian</summary>

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk postgresql curl
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt install -y nodejs
```
</details>

<details>
<summary>Instalar no macOS (Homebrew)</summary>

```bash
brew install openjdk@17 node@20 postgresql@16 && brew services start postgresql@16
```
</details>

---

## 3. Instalação passo a passo

### 3.1 Clone o repositório

```bash
git clone https://github.com/kekoksg/mealmath.git && cd mealmath
```

### 3.2 Crie o banco

```bash
sudo -u postgres psql -c "CREATE DATABASE mealmath_db;"
```

As tabelas são criadas sozinhas no primeiro boot (`ddl-auto=update`) — não há script de
schema para rodar à mão.

### 3.3 Configure os segredos

Nenhuma credencial está versionada. Copie o exemplo:

```bash
cp backend/mealmath-api/.env.example backend/mealmath-api/.env
```

Gere uma chave JWT — precisa ter no mínimo 32 bytes, senão a aplicação recusa subir:

```bash
openssl rand -base64 48
```

Abra `backend/mealmath-api/.env` e preencha `DB_SENHA` com a senha do seu PostgreSQL e
`APP_JWT_SEGREDO` com a chave gerada. Os detalhes de cada variável estão na
[seção 8](#8-configuração).

### 3.4 Instale as dependências do front

```bash
cd frontend && npm install && cd ..
```

### 3.5 Suba

```bash
./subir.sh --local
```

O script sobe a API na `:8082`, faz o build de produção do Angular e serve o SSR na
`:4000` — que encaminha `/api` para o backend, então o navegador conversa com uma origem
só. `Ctrl+C` derruba tudo.

Abra **<http://localhost:4000>**.

> **Conta de exemplo:** `maria@email.com` / `senha123`
> O perfil `dev` semeia 12 itens de mercado com histórico de preços, 4 refeições-modelo,
> duas semanas de diário e uma meta — o suficiente para o dashboard ter o que mostrar.
> A carga é idempotente por e-mail: subir de novo não duplica nada.

Sem o `--local`, o script ainda publica uma URL HTTPS pública via Cloudflare Tunnel
(exige o `cloudflared` instalado) — é assim que se abre no celular.

---

## 4. Primeiro uso — do zero ao dashboard

Criando a sua própria conta, a ordem importa: **item → refeição → diário → meta**. Uma
refeição só tem custo se os itens dela tiverem preço, e o dashboard só tem o que somar
depois que existir registro no diário.

### Passo 1 — Crie a conta

Em **Criar conta**, informe nome, e-mail e senha (8 a 72 caracteres). Você já entra
autenticado, direto na Visão Geral — que vai estar vazia, e é isso mesmo.

### Passo 2 — Cadastre o que você compra 🛒

Aba **Mercado** → botão **+** → *Novo item*:

| Campo | Exemplo |
|---|---|
| Nome do item | `Peito de frango` |
| Preço pago (R$) | `18,90` |
| Categoria | `Proteína` |
| Quantidade | `1` |
| Unidade | `kg` |

O card verde mostra o **custo unitário** em tempo real enquanto você digita — aqui,
`R$ 1,89 /100g`. É a prova de que a conversão está certa antes mesmo de salvar.

Cadastre uns 5 ou 6 itens do que você realmente compra. Embalagem em `g` e `mL` vale
igual: `Aveia · R$ 9,50 · 500 g` é tão válido quanto `1 kg`.

### Passo 3 — Monte suas refeições 🍽️

Aba **Refeição** → **+** → *Nova refeição*:

1. Escolha um **ícone** e dê um **título** (`Café da manhã`).
2. **+ Adicionar item do mercado** — escolha o item e informe **quanto você come**
   (ex.: `40 g` de aveia).
3. Repita para cada ingrediente. O **Custo da refeição** vai se atualizando no rodapé.
4. **Criar refeição**.

Isto é um *modelo*, não um consumo. Ele não entra em nenhuma conta ainda.

### Passo 4 — Registre o que você comeu 📅

Aba **Diário** → escolha o dia na faixa da semana → **+** → *Adicionar ao dia*:

Escolha a refeição na lista e toque no **+**. Ela entra na data **como cópia** — está
escrito na própria tela:

> *"A refeição entra em 25/08/2026 como cópia: ajustar o que foi comido neste dia não
> altera o modelo."*

Comeu mais frango hoje? Ajuste a quantidade **no registro do dia**. O modelo da
biblioteca continua intacto, e o almoço de ontem também. Se o dia foi igual ao anterior,
use **repetir dia anterior** em vez de remontar tudo.

### Passo 5 — Defina sua meta 🎯

**Perfil** (ícone no canto superior direito da Visão Geral) → *Meta de orçamento* →
**Editar**: informe o valor-limite e escolha **Por semana** ou **Por mês**.

A meta é rateada pela duração do período consultado — um limite mensal de R$ 250,00
visto na aba *Semana* vale a fração equivalente a 7 dias.

### Passo 6 — Leia a Visão Geral 📊

Agora o dashboard tem o que mostrar. Alterne entre **Hoje / Semana / Mês** e leia:

- **Custo total** do período e a variação contra o período anterior
- **Progresso da meta**, com o quanto falta ou o quanto passou
- **Onde vai o dinheiro** — a divisão por categoria
- **Itens de maior impacto** e **altas de preço** recentes
- **Completude do diário** (`N de M dias com registro`)

Um indicador que **some** não é bug: sem período anterior, não existe percentual honesto
a exibir — é melhor esconder do que inventar um `+100%`. Item sem preço vinculado também
não vira R$ 0,00 silenciosamente; ele é excluído do total e listado à parte.

### Passo 7 — Atualize um preço 📈

O frango subiu? Aba **Mercado** → toque no item → altere o preço → salvar.

O valor antigo não é sobrescrito: ele é empilhado no histórico do item, com a data. Os
custos passam a usar o preço novo e as **altas de preço** aparecem no dashboard.

---

## 5. Instalar no celular

O MealMath é uma PWA. Para instalar de verdade é preciso HTTPS e **endereço fixo** — um
app instalado hoje num túnel efêmero aponta para um endereço morto amanhã.

**Só para testar na rede local** (sem instalar), rode em modo dev e acesse pelo IP do PC:

```bash
cd frontend && npx ng serve --host 0.0.0.0
```

No celular: `http://IP-DO-SEU-PC:4200`. O front resolve a API pelo mesmo host que serviu
a página, então funciona sem configuração extra.

**Para instalar como app**, você precisa de uma URL HTTPS estável — um Cloudflare Tunnel nomeado
(com domínio próprio) ou ngrok com domínio estático. O `subir.sh` aceita as variáveis
`MEALMATH_TUNEL` e `MEALMATH_HOST` para usar o túnel nomeado em vez do efêmero, que sorteia um
endereço novo a cada execução e invalida a instalação anterior.

---

## 6. Modo desenvolvimento

Rode as duas pontas separadas, cada uma com seu hot reload:

**Backend** — `:8082`, com a carga de exemplo:

```bash
cd backend/mealmath-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Se o `.env` não for lido automaticamente pelo seu setup, carregue-o antes:

```bash
set -a; . ./.env; set +a
```

**Frontend** — `:4200`, apontando para a `:8082`:

```bash
cd frontend && npm start
```

**Documentação interativa da API:** <http://localhost:8082/swagger-ui.html> — o botão
*Authorize* aceita o JWT devolvido pelo login, então dá para exercitar todos os endpoints
pelo navegador.

**Build de produção:**

```bash
cd frontend && npx ng build --configuration production
```

```bash
cd backend/mealmath-api && ./mvnw clean package
```

---

## 7. Testes

```bash
cd backend/mealmath-api && ./mvnw test
```

```bash
cd frontend && npx ng test --watch=false
```

**216 testes no backend** — a conversão de unidades caso a caso, os testes de integração
de cada controller (incluindo tentativas de acessar dado de outro usuário) e as
invariantes de borda: divisão por zero, grandeza incompatível (`g` de um item vendido em
`L`), item sem preço, período anterior vazio, cópia profunda no diário.
**121 testes no frontend**, sobre serviços, validadores e componentes.

> Os testes de integração usam o banco configurado no `.env`. Aponte `DB_URL` para um
> banco separado se não quiser que eles toquem no seu banco de desenvolvimento.

---

## 8. Configuração

Arquivo `backend/mealmath-api/.env` (fora do controle de versão). Variáveis de
ambiente de verdade têm precedência sobre o arquivo, então dá para sobrescrever
pontualmente em CI ou deploy.

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/mealmath_db` | JDBC do PostgreSQL |
| `DB_USUARIO` | `postgres` | usuário do banco |
| `DB_SENHA` | — | **obrigatória.** Sem default de propósito: melhor falhar no boot do que subir com senha versionada |
| `APP_JWT_SEGREDO` | — | **obrigatória.** Chave HS256, mínimo 32 bytes (`openssl rand -base64 48`) |
| `APP_CORS_ORIGENS` | `http://localhost:4200,...` | origens liberadas, separadas por vírgula |

Outras portas e tempos ficam em `application.properties`: API na `8082`, expiração do
token em `8h`, SSR na `4000` (ou `PORT`).

---

## 9. Deu problema?

<details>
<summary><b>A aplicação não sobe: <code>Could not resolve placeholder 'DB_SENHA'</code></b></summary>

O `.env` não existe ou não tem `DB_SENHA`. Refaça o [passo 3.3](#33-configure-os-segredos).
É proposital: a aplicação prefere falhar com erro claro a subir com uma senha padrão.
</details>

<details>
<summary><b><code>app.jwt.segredo precisa de no mínimo 32 bytes para HS256</code></b></summary>

A chave está curta. Gere de novo com `openssl rand -base64 48` e cole o valor inteiro.
</details>

<details>
<summary><b>Não consigo trocar a unidade de um item: <code>409 Conflict</code></b></summary>

Você está mudando a **grandeza** do item (de `kg` para `L`, por exemplo) enquanto ele já é
usado em alguma refeição ou registro do diário. Não há conversão possível entre massa e
volume, e aceitar a troca corromperia os custos já consolidados. Trocar entre `kg` e `g`
(mesma grandeza) funciona normalmente.

Excluir o item, por outro lado, sempre funciona: é exclusão lógica — ele some da lista mas
continua no banco, preservando o custo histórico do diário.
</details>

<details>
<summary><b><code>Connection refused</code> ao PostgreSQL</b></summary>

O serviço não está rodando ou o banco não existe:

```bash
sudo systemctl status postgresql && sudo -u postgres psql -c "\l" | grep mealmath
```
</details>

<details>
<summary><b>Porta 8082 / 4000 / 4200 ocupada</b></summary>

```bash
ss -ltnp | grep -E ':(8082|4000|4200)'
```

Encerre o processo, ou mude a porta: `server.port` no `application.properties` para a API,
`PORT=4001 ./subir.sh --local` para o SSR.
</details>

<details>
<summary><b>O dashboard abre em "Sem registros no período"</b></summary>

Com a conta de exemplo: a carga do perfil `dev` é gerada a partir da data em que rodou
pela primeira vez e envelhece. Recrie os dados (ou apague o usuário `maria@email.com` e
suba de novo) para reancorá-los em torno de hoje.

Com a sua conta: é o estado vazio correto — falta registrar consumo no Diário
([passo 4](#passo-4--registre-o-que-você-comeu-)).
</details>

<details>
<summary><b>Erro de CORS ao abrir pelo celular</b></summary>

A origem do celular (`http://IP-DO-PC:4200`) não está liberada. Acrescente-a em
`APP_CORS_ORIGENS`, separada por vírgula, e reinicie a API. Pelo `subir.sh` isso não
acontece: o SSR proxia `/api`, então não há requisição cross-origin.
</details>

<details>
<summary><b><code>cloudflared: command not found</code></b></summary>

O túnel público é opcional. Use `./subir.sh --local` para subir sem expor nada na internet.
</details>

---

## 10. Estrutura do código

```
├── backend/mealmath-api/             Spring Boot · API REST stateless
│   └── src/main/java/.../
│       ├── domain/                   entidades JPA, enums e exceções de negócio
│       ├── dto/                      records Java 17 — entidade não cruza o controller
│       ├── repository/               toda query filtra pelo usuário do token
│       ├── service/                  conversão de unidade, cálculo de custo, regras
│       ├── security/                 JWT (Nimbus), bcrypt, resolução do usuário
│       ├── config/                   OpenAPI, seed de desenvolvimento, migrações
│       └── controller/               orquestração + RestExceptionHandler global
│
├── frontend/src/app/
│   ├── core/                         auth, guards, interceptor, domínio compartilhado
│   ├── features/                     dashboard · dieta · mercado · perfil · login · cadastro
│   ├── layout/                       shell + bottom nav
│   └── shared/                       bottom sheet, ícones, pipe de moeda
│
├── docs/telas/                       capturas de tela da aplicação
└── subir.sh                          sobe tudo (API + SSR + túnel opcional)
```

### Onde mexer para cada coisa

| Quero... | Vá para |
|---|---|
| mudar uma regra de cálculo | `service/CalculadoraCustoService.java` |
| adicionar uma unidade de medida | `domain/UnidadeMedida.java` + `service/ConversorUnidadeService.java` |
| mudar o que o dashboard mostra | `service/DashboardService.java` + `features/dashboard/` |
| adicionar um campo em um payload | o record em `dto/` (é o contrato do JSON) |
| mudar mensagem de erro da API | `controller/RestExceptionHandler.java` |
| ajustar cores, espaçamento, tipografia | `frontend/src/styles/` |

### Convenções que o código segue

- **`BigDecimal` em toda a camada monetária.** Nunca `double`. Arredondamento
  (`HALF_UP`, 2 casas) só na exibição — arredondar valor intermediário distorce o total.
- **Regra de negócio mora no `Service`.** Controller orquestra e valida entrada.
- **Nenhuma entidade JPA cruza o controller.** Só records de `dto/`.
- **Toda query filtra pelo usuário autenticado**, no repositório. Recurso de outro
  usuário responde `404`, não `403` — nem a existência do registro vaza.
- **`LocalDate` no diário**, sem hora nem fuso: a refeição não muda de dia conforme o
  fuso do navegador.
- **`JOIN FETCH` / `@EntityGraph`** ao carregar refeição com itens, contra N+1.
- **Angular standalone**, `inject()`, tipagem estrita, mobile-first a partir de 390 px.
- **A fonte da verdade é a API.** `localStorage` guarda o token, nunca dado de domínio.
- **Estado vazio é acionável**: nunca só "sem itens" — sempre com o próximo passo.

---

<div align="center">
<sub>Kelvin Souza Gonçalves · <a href="https://github.com/kekoksg">@kekoksg</a></sub>
</div>
