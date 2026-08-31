<div align="center">

# 🥗 MealMath

**Você sabe quanto gastou no mercado. Sabe quanto custou o seu almoço?**

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20.3-DD0031?logo=angular&logoColor=white)](https://angular.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
![PWA](https://img.shields.io/badge/PWA-instalável-5A0FC8?logo=pwa&logoColor=white)
[![Testes](https://img.shields.io/badge/testes-216%20back%20%2B%20121%20front-success)](#testes)

Projeto Integrador II · Análise e Desenvolvimento de Sistemas · Unesc

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

Quatro escolhas explicam a maior parte do comportamento do sistema:

**Dinheiro em `BigDecimal`, arredondado só na exibição.** `double` acumula erro de ponto flutuante,
e arredondar item a item antes de somar distorce o total. A soma acontece na escala cheia; o
`HALF_UP` com 2 casas é a última coisa que acontece.

**Isolamento por usuário no repositório, não no controller.** O filtro está na assinatura do
método (`findByUsuarioIdAnd...`), então a query já nasce restrita. Pedir um recurso de outra conta
devolve `404`, nunca `403` — o `403` confirmaria que aquele registro existe.

**Preço nunca é sobrescrito.** Ao atualizar, o valor antigo vai para `HistoricoPreco`. O custo de
ontem continua calculado com o preço de ontem, e a variação vira informação consultável em vez de
se perder no `UPDATE`.

**Item sem preço fica fora do total e é sinalizado na tela.** Somar como R$ 0,00 esconderia gasto
real, o que é pior do que admitir a lacuna.

### Biblioteca ≠ Diário

A distinção que evita o erro mais caro do domínio:

- **Biblioteca (`Refeicao`)** é o modelo reutilizável — ícone, título, itens com quantidades
  padrão.
- **Diário (`RegistroDiario`)** é a instância consumida numa data, com cópia própria dos itens.

Mudar a quantidade de frango no almoço de hoje não mexe no modelo "Almoço", nem no almoço de
ontem. A instanciação faz cópia profunda, e o dashboard soma sempre o Diário.

## Arquitetura

```
mealmath/
├── backend/mealmath-api/        Spring Boot · API REST stateless
│   └── src/main/java/.../
│       ├── domain/              entidades JPA + enums + exceções de negócio
│       ├── dto/                 records Java 17 (nenhuma entidade cruza o controller)
│       ├── repository/          queries sempre filtradas por usuário
│       ├── service/             conversão, cálculo e regras de negócio
│       ├── security/            JWT (Nimbus), bcrypt, resolução do usuário do token
│       └── controller/          orquestração + RestExceptionHandler global
├── frontend/                    Angular standalone · SSR · PWA
│   └── src/app/
│       ├── core/                auth, interceptor, domínio compartilhado
│       ├── features/            dashboard · dieta · mercado · perfil · login · cadastro
│       ├── layout/              shell + bottom nav
│       └── shared/              bottom sheet, ícones, pipe de moeda
└── docs/telas/                  capturas de tela da aplicação
```

No backend, a API é stateless com JWT (HS256, 8 h) e senhas em bcrypt. Cálculo e conversão ficam
no `Service`; o controller orquestra e valida entrada. Refeições com itens são carregadas por
`JOIN FETCH` ou `@EntityGraph` para não cair em N+1. Datas do diário usam `LocalDate`, sem fuso,
para a refeição não trocar de dia conforme o relógio do navegador.

No frontend, componentes standalone com `inject()` e tipagem estrita, desenhados para 390 px de
largura. A fonte da verdade é a API: o `localStorage` guarda o token e nada de domínio. O SSR roda
em Express e o service worker torna a aplicação instalável.

## Rodando localmente

Você vai precisar de JDK 17+, Node 20+ e um PostgreSQL com um banco criado (o padrão é
`mealmath_db`).

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

### 2. Suba tudo de uma vez

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

A documentação interativa da API fica em <http://localhost:8082/swagger-ui.html>.

## Testes

```bash
cd backend/mealmath-api && ./mvnw test
```

```bash
cd frontend && npx ng test --watch=false
```

São **216 testes no backend**: os casos canônicos de conversão, testes de integração de cada
controller (incluindo isolamento entre contas) e as invariantes de borda — divisão por zero,
grandeza incompatível, item sem preço, período anterior vazio, cópia profunda no diário. No
frontend são **121**, cobrindo serviços, validadores e componentes de tela.

```bash
cd frontend && npm run contraste
```

Esse último mede **26 pares de cor** contra a WCAG 2.1 AA, cada cor no papel em que é usada:
4,5:1 para texto, 3:1 para componente de interface e objeto gráfico. O script lê os valores direto
de `_tokens.scss` e sai com erro se algum par reprovar.

## API

Base `http://localhost:8082`, JWT em `Authorization: Bearer <token>` para tudo que não seja
`/auth/**`.

| Recurso | Endpoints |
|---|---|
| **Autenticação** | `POST /auth/registrar` · `POST /auth/login` |
| **Itens de mercado** | `GET POST /itens-mercado` · `GET PUT DELETE /itens-mercado/{id}` · `GET /itens-mercado/{id}/historico` |
| **Biblioteca** | `GET POST /refeicoes` · `GET PUT DELETE /refeicoes/{id}` |
| **Diário** | `GET POST /registros-diarios` · `GET DELETE /registros-diarios/{id}` · `PATCH /registros-diarios/{registroId}/itens/{itemId}` · `POST /registros-diarios/duplicar-dia-anterior` |
| **Meta** | `GET PUT DELETE /meta-orcamento` |
| **Dashboard** | `GET /dashboard` |
| **Perfil** | `GET PUT /perfil` · `PUT /perfil/senha` |

Campos, validações, códigos de erro e exemplos de payload ficam na documentação interativa gerada
pelo springdoc, em <http://localhost:8082/swagger-ui.html> com a aplicação no ar.

## Requisitos funcionais

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

---

<div align="center">
<sub>Kelvin Souza Gonçalves · Unesc · 2026</sub>
</div>
