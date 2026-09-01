<div align="center">

# 🥗 MealMath

**Você sabe quanto gastou no mercado. Sabe quanto custou o seu almoço?**

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20.3-DD0031?logo=angular&logoColor=white)](https://angular.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
![PWA](https://img.shields.io/badge/PWA-instalável-5A0FC8?logo=pwa&logoColor=white)

**[Guia de uso: instalar, rodar e desenvolver do zero](COMO-USAR.md)**

</div>

---

## O problema

O gasto com comida é dos maiores de uma casa e o único que se refaz três vezes por dia. Mesmo
assim é o menos controlado, porque o número só aparece agregado: o total do supermercado, no
extrato, com o mês já fechado. Quanto custou o almoço de terça? Qual refeição pesa mais? Ainda dá
para ajustar o jantar de hoje? Sem resposta no dia, não há ajuste a tempo.

A conta não fecha na cabeça porque a unidade da compra não é a unidade do prato. Frango sai a
R$ 18,90 o quilo e vira 150 g no almoço, R$ 2,84. Multiplique por dez itens, cinco refeições e
trinta dias: ninguém faz isso à mão.

O MealMath faz o rateio e consolida o resultado — custo por refeição, por dia e por período,
comparado com o período anterior e com a meta que você definiu.

Os apps de dieta resolvem a camada nutricional e ignoram a financeira: sabem as calorias do seu
almoço, não os reais. Os de finanças param em "supermercado" como despesa única e nunca chegam ao
prato. Planilha tem a matemática, mas não sobrevive ao uso diário. Este projeto fica no vão entre
os três.

## Telas

<table>
  <tr>
    <td align="center"><img src="docs/telas/03-dashboard.png" width="200" alt="Dashboard"><br><sub><b>Visão Geral</b><br>custo, meta e composição</sub></td>
    <td align="center"><img src="docs/telas/06-diario.png" width="200" alt="Diário"><br><sub><b>Diário</b><br>o que foi consumido no dia</sub></td>
    <td align="center"><img src="docs/telas/05-refeicoes.png" width="200" alt="Refeições"><br><sub><b>Biblioteca</b><br>refeições-modelo reutilizáveis</sub></td>
    <td align="center"><img src="docs/telas/04-mercado.png" width="200" alt="Mercado"><br><sub><b>Mercado</b><br>preços e histórico</sub></td>
  </tr>
</table>

<sub>As 20 capturas estão em <a href="docs/telas">docs/telas</a>.</sub>

## Decisões de domínio

São as escolhas que explicam o comportamento do sistema — e as que eu defenderia numa conversa
técnica, porque nenhuma delas é consequência automática do framework.

**Dinheiro em `BigDecimal`, arredondado só na exibição.** `double` acumula erro de ponto flutuante,
e arredondar item a item antes de somar distorce o total. A soma acontece na escala cheia; o
`HALF_UP` com 2 casas é a última coisa que acontece.

**Preço nunca é sobrescrito.** Ao atualizar, o valor antigo vai para `HistoricoPreco`. Além disso,
o registro do diário guarda o preço congelado no momento do consumo: mudar o preço do frango hoje
não reescreve quanto custou o almoço da semana passada. O custo de ontem continua sendo o custo de
ontem.

**Item sem preço fica fora do total e é sinalizado na tela.** Somar como R$ 0,00 esconderia gasto
real, o que é pior do que admitir a lacuna. R$ 0,00 e "não sei o preço" são coisas diferentes, e o
sistema não finge que são a mesma.

**Dias sem registro não contam como R$ 0,00.** A média divide pelos dias do período, mas o
indicador "N de M dias" existe para expor a lacuna em vez de deixá-la diluída na média.

### Biblioteca ≠ Diário

A distinção que evita o erro mais caro do domínio:

- **Biblioteca (`Refeicao`)** é o modelo reutilizável — ícone, título, itens com quantidades
  padrão. Não tem data.
- **Diário (`RegistroDiario`)** é a instância consumida numa data, com cópia própria dos itens.

Mudar a quantidade de frango no almoço de hoje não mexe no modelo "Almoço", nem no almoço de
ontem. A instanciação faz cópia profunda, e o dashboard soma sempre o Diário.

## Decisões de segurança

**Isolamento por usuário no repositório, não no controller.** O filtro está na assinatura do
método (`findByUsuarioIdAnd...`), então a query já nasce restrita e não existe caminho em que
alguém esqueça de checar. Pedir um recurso de outra conta devolve `404`, nunca `403` — o `403`
confirmaria que aquele registro existe.

A resposta HTTP não distingue "não existe" de "não é seu", e isso é intencional. Mas a aplicação
distingue internamente: tentativa de acesso a recurso de outra conta sai no log como alerta, com o
tipo do recurso, o id pedido e o id de quem pediu; id que simplesmente não existe é ruído de link
velho e fica em `debug`, para não afogar o caso que importa. Esconder a informação do cliente não é
a mesma coisa que ficar cego para ela.

O log carrega só ids — nunca e-mail, nome ou token. Quem investigar cruza o id com o banco, e o
registro de segurança não vira mais um lugar por onde dado pessoal vaza. A consulta que confere o
dono só roda no caminho de erro, depois da busca escopada já ter voltado vazia: requisição que dá
certo não paga nada por essa auditoria.

**Token JWT no `localStorage`.** É o trade-off mais explícito do projeto, então vale nomeá-lo: o
`localStorage` é acessível por JavaScript, o que significa que uma vulnerabilidade de XSS na
aplicação daria acesso ao token. A alternativa é o cookie `HttpOnly`, que fecha essa superfície
mas traz CSRF para o escopo e complica o SSR. Para um projeto de conta única e dados do próprio
usuário, a troca compensou; para dados de terceiros eu reavaliaria. O que o `localStorage` nunca
guarda é dado de domínio — só o token.

Senhas em bcrypt e JWT HS256 com expiração de 8 h. O segredo fica fora do controle de versão: a
aplicação não sobe sem `APP_JWT_SEGREDO` e recusa chave com menos de 32 bytes, para que uma chave
fraca falhe no boot em vez de passar despercebida.

## Arquitetura

```
mealmath/
├── backend/mealmath-api/        Spring Boot · API REST stateless
│   └── src/main/java/.../
│       ├── domain/              entidades, enums e exceções de negócio
│       ├── dto/                 records de entrada e saída
│       ├── repository/          acesso a dados, escopado por usuário
│       ├── service/             regras de negócio, conversão e cálculo
│       ├── security/            autenticação e resolução do usuário
│       └── controller/          borda HTTP
├── frontend/                    Angular standalone · SSR · PWA
│   └── src/app/
│       ├── core/                auth, guard, interceptor
│       ├── features/            uma pasta por tela
│       ├── layout/              shell e navegação
│       └── shared/              componentes e pipes reutilizados
└── docs/telas/                  capturas de tela da aplicação
```

**Backend.** Quatro camadas com fronteira explícita. O `controller` recebe, valida a entrada e
delega; nenhuma entidade JPA atravessa essa fronteira, só records. O `service` é onde vivem
conversão de unidade, cálculo de custo e regras de negócio — não há regra escondida em controller
nem em entidade. O `repository` é onde o isolamento por usuário é garantido, na assinatura do
método. A API é stateless: nada de sessão no servidor, o dono da requisição sai do token.

**Frontend.** A API é a fonte da verdade e o estado da interface reflete a resposta assíncrona;
não há cache de domínio no cliente. `core` concentra autenticação e interceptação, `features` tem
uma pasta por tela, `layout` guarda o shell e a navegação, `shared` o que é reaproveitado entre
telas.

### Notas de implementação

Detalhes que não mudam a estrutura acima, mas respondem "por que assim":

- Refeições com seus itens são carregadas com `JOIN FETCH` / `@EntityGraph`, para a listagem não
  degenerar em N+1.
- Datas do diário são `LocalDate`, sem hora nem fuso, para a refeição não trocar de dia conforme o
  relógio do navegador de quem acessa.
- Componentes Angular standalone, injeção via `inject()`, TypeScript em modo estrito.
- A interface é desenhada para 390 px de largura e escala para cima, não o contrário.
- SSR em Express e service worker registrado: a aplicação é instalável.

## A API

Base `http://localhost:8082`. Tudo exige `Authorization: Bearer <token>`, exceto `/auth/**`.

**Itens de mercado** — `GET POST /itens-mercado` · `GET PUT DELETE /itens-mercado/{id}` ·
`GET /itens-mercado/{id}/historico`
O que você compra, na unidade em que compra: preço, quantidade da embalagem e unidade. O custo
unitário é derivado disso, normalizado para `g`, `mL` ou `un`. Atualizar o preço empilha o valor
anterior no histórico, que é o que o último endpoint devolve. O `DELETE` é exclusão lógica: o item
sai da lista, mas o diário continua conseguindo calcular o custo do que já foi consumido.

**Biblioteca de refeições** — `GET POST /refeicoes` · `GET PUT DELETE /refeicoes/{id}`
Modelos reutilizáveis, com título, ícone e itens em quantidades padrão. Nada aqui tem data: é a
forma da refeição, não uma refeição consumida.

**Diário** — `GET POST /registros-diarios` · `GET DELETE /registros-diarios/{id}` ·
`PATCH /registros-diarios/{registroId}/itens/{itemId}` ·
`POST /registros-diarios/duplicar-dia-anterior`
Instancia um modelo da biblioteca numa data. O registro nasce com cópia própria dos itens e com o
preço congelado, então nem editar o modelo nem mexer no preço do item altera o que aquele dia já
custou. O `PATCH` ajusta a quantidade de um item só naquele registro. O `duplicar-dia-anterior`
repete o dia anterior, também por cópia.

**Meta de orçamento** — `GET PUT DELETE /meta-orcamento`
Mensal ou semanal. Sem meta definida o dashboard omite o progresso e oferece defini-la, em vez de
exibir 0%.

**Dashboard** — `GET /dashboard?periodo=DIA|SEMANA|MES` (padrão `SEMANA`)
Consolida o custo do período, compara com o período anterior, mede o progresso da meta e informa a
completude do diário. Responde `200` mesmo sem nenhum registro — período vazio é estado normal de
quem acabou de começar. Campos nulos em `comparativo` e `meta` também são estado, não erro: sem
período anterior não existe variação percentual para mostrar, e o front oculta o indicador.

**Perfil** — `GET PUT /perfil` · `PUT /perfil/senha`
A troca de senha responde `400` quando a senha atual não confere, não `401`. O token continua
válido; um `401` faria o front derrubar a sessão em vez de apontar o campo errado.

### Um fluxo completo

1. `POST /auth/registrar`, depois `POST /auth/login` → devolve o token.
2. `POST /itens-mercado` → frango, R$ 18,90, embalagem de 1 kg. O custo unitário sai em R$/g.
3. `POST /refeicoes` → "Almoço", com 150 g de frango entre os itens.
4. `POST /registros-diarios` → instancia o Almoço na data de hoje. O registro copia os itens e
   congela o preço vigente.
5. `GET /dashboard?periodo=MES` → os R$ 2,84 desse almoço já entram no total do mês, na média por
   dia e na comparação com o mês anterior.

Campos, validações, códigos de erro e exemplos de payload ficam na documentação interativa gerada
pelo springdoc, em <http://localhost:8082/swagger-ui.html> com a aplicação no ar.

## Rodando localmente

Você vai precisar de JDK 17+, Node 20+ e Docker — ou um PostgreSQL instalado, se preferir.

### 1. Configure os segredos

O backend se recusa a subir com credencial versionada. Copie o exemplo e preencha:

```bash
cp backend/mealmath-api/.env.example backend/mealmath-api/.env
```

| Variável | Descrição |
|---|---|
| `DB_URL` | JDBC do PostgreSQL (padrão `jdbc:postgresql://localhost:5432/mealmath_db`) |
| `DB_USUARIO` | usuário do banco |
| `DB_SENHA` | senha do banco. Sem default: a aplicação falha no boot sem ela |
| `APP_JWT_SEGREDO` | chave HS256, mínimo 32 bytes. Gere com `openssl rand -base64 48` |
| `APP_CORS_ORIGENS` | origens liberadas, separadas por vírgula (opcional) |

### 2. Suba o banco

```bash
set -a; . backend/mealmath-api/.env; set +a
docker compose up -d --wait
```

Sobe só o PostgreSQL, já com o banco criado, lendo as credenciais do mesmo `.env` que a aplicação
usa — assim a senha existe em um lugar só. O `--wait` segura até o healthcheck passar, então o
comando seguinte já encontra o banco aceitando conexão. Se a `5432` estiver ocupada, use
`PORTA_POSTGRES=5433 docker compose up -d --wait` e ajuste o `DB_URL`.

Se preferir o PostgreSQL instalado na máquina, crie um banco `mealmath_db` e pule este passo.

### 3. Suba a aplicação

```bash
./subir.sh --local
```

O script carrega o `.env`, sobe a API na `:8082` com o perfil `dev` (carga de exemplo: 12 itens de
mercado com histórico, 4 refeições-modelo, duas semanas de diário e uma meta), faz o build de
produção do front e serve o SSR na `:4000`, encaminhando `/api` para o backend.

Login de exemplo: `maria@email.com` / `senha123`

Sem o `--local`, o script também publica uma URL HTTPS via Cloudflare Tunnel, o que é útil para
abrir no celular e instalar como app.

### Ou cada parte separadamente

```bash
cd backend/mealmath-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # :8082
```

```bash
cd frontend && npm install && npm start                                            # :4200
```

## Testes

O que a suíte protege, em ordem de importância:

**A conta que o sistema existe para fazer.** Os casos canônicos de conversão são teste, não
comentário: frango a R$ 18,90/kg consumido em 150 g dá R$ 2,835; aveia comprada em pacote de 500 g
e consumida em 40 g dá R$ 0,76; leite em litro consumido em mL; ovos por unidade; meia unidade de
brócolis, que não pode ser arredondada.

**O isolamento entre contas.** Cada controller tem teste de integração que pede um recurso de
outro usuário e exige `404` — é a garantia de que a regra do repositório não foi contornada em
algum caminho novo.

**As invariantes de borda**, que são onde este tipo de sistema quebra em silêncio: divisão por
zero, grandeza incompatível (`g` de um item vendido em `L`), item sem preço fora do total, período
anterior vazio sem variação percentual, e a cópia profunda que impede a edição de um dia de vazar
para outro.

**O contraste da interface.** 26 pares de cor medidos contra a WCAG 2.1 AA, cada cor no papel em
que é usada: 4,5:1 para texto, 3:1 para componente de interface e objeto gráfico. O script lê os
valores direto de `_tokens.scss` e falha se algum par reprovar.

```bash
cd backend/mealmath-api && ./mvnw test          # 216 testes
```

```bash
cd frontend && npx ng test --watch=false        # 121 testes
```

```bash
cd frontend && npm run contraste                # 26 pares de cor
```

Os números estão aí para referência, mas eles importam menos que as categorias acima — suíte
grande não é o mesmo que suíte útil.

---

## Apêndice: contexto acadêmico

Este projeto nasceu como Projeto Integrador II do curso de Análise e Desenvolvimento de Sistemas
da Unesc. Os requisitos levantados na disciplina e o estado de cada um:

| ID | Requisito | Status |
|---|---|:--:|
| RF001 | Cadastrar usuário | ✔ |
| RF002 | Autenticar usuário (JWT) | ✔ |
| RF003 | Cadastrar refeição na biblioteca | ✔ |
| RF004 | Cadastrar item de mercado com embalagem base | ✔ |
| RF005 | Calcular custo fracionado | ✔ |
| RF006 | Consolidar custo por período (dashboard) | ✔ |
| RF007 | Atualizar preço, recalcular e registrar histórico | ✔ |
| RF009 | Registrar consumo no diário | ✔ |
| RF010 | Definir meta de orçamento | ✔ |

<sub>RF008 (simulador de substituição de itens) saiu do escopo e o número não foi reaproveitado.</sub>

<div align="center">
<sub>Kelvin Souza Gonçalves · Unesc · 2026</sub>
</div>
