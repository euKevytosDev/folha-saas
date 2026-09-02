# Folha

Plataforma SaaS de pedidos online para sacolões, hortifrutis, mercados e pequenos comércios de alimentos.

Cada estabelecimento recebe a própria loja pública no celular do cliente, um painel operacional e isolamento completo de dados em relação aos demais tenants.

## Arquitetura

Monólito modular em Spring Boot. Um único deploy, pacotes separados por domínio.

```
backend/   API REST, segurança, persistência e páginas estáticas
frontend/  HTML, CSS e JavaScript puro (mobile first)
```

A API vive em `/api/v1`. O frontend é servido pelo próprio Spring Boot.

### Multi-tenant

Schema compartilhado no PostgreSQL, com `establishment_id` em toda entidade operacional.

- Painel autenticado: o tenant sai do JWT.
- Loja pública: o tenant sai do `slug` da URL.
- `SUPER_ADMIN` não carrega tenant e não acessa dados operacionais sem ação explícita.
- O frontend nunca é a fonte da verdade do isolamento.

### Autenticação (a partir da Fase 2)

Spring Security + JWT.

- Access token curto
- Refresh token persistido apenas como hash
- Senhas com BCrypt
- Roles: `SUPER_ADMIN`, `OWNER`, `ADMIN`, `STAFF`

### Dinheiro e auditoria

Valores monetários usam `BigDecimal` / `NUMERIC`. Pedidos guardam snapshot de preço, produto e endereço. O servidor recalcula totais.

## Tecnologias

- Java 21
- Spring Boot 4.1
- Spring Web MVC, Security, Data JPA, Validation
- PostgreSQL 16
- Flyway
- HTML5, CSS3, JavaScript (ES modules) e Fetch API

## Como executar localmente

### 1. PostgreSQL

Com Docker:

```bash
docker compose up -d
```

Sem Docker, crie o banco `folha` e o usuário `folha`.

Se a porta `5432` já estiver ocupada, use outra porta e ajuste `DATABASE_URL`.

### 2. Variáveis de ambiente

```bash
cp .env.example .env
```

Nunca commite o arquivo `.env`.

### 3. Subir a aplicação

```bash
chmod +x scripts/dev.sh
./scripts/dev.sh
```

Ou:

```bash
cd backend
mvn spring-boot:run
```

Abra [http://localhost:8080](http://localhost:8080).

A API de saúde responde em [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health).

## Variáveis de ambiente

| Variável | Descrição |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` ou `prod` |
| `SERVER_PORT` | Porta HTTP (padrão `8080`) |
| `DATABASE_URL` | JDBC do PostgreSQL |
| `DATABASE_USERNAME` | Usuário do banco |
| `DATABASE_PASSWORD` | Senha do banco |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula |
| `JWT_SECRET` | Segredo do JWT (mínimo 32 bytes, só em variável de ambiente) |
| `JWT_ACCESS_EXPIRATION_MS` | Expiração do access token |
| `JWT_REFRESH_EXPIRATION_MS` | Expiração do refresh token |
| `FRONTEND_DIR` | Pasta do frontend em desenvolvimento; vazio em produção |

## Banco e migrations

O Hibernate **não** cria tabelas. Toda mudança de schema passa pelo Flyway em `backend/src/main/resources/db/migration`.

A Fase 1 cria:

- `establishments`
- `users`
- `refresh_tokens`
- `password_reset_tokens`

IDs são UUID. Datas em `timestamptz` (UTC).

## Usuários iniciais

Ainda não há seed. Login, cadastro e SUPER_ADMIN entram na Fase 2.

## Endpoints principais

| Método | Caminho | Descrição |
| --- | --- | --- |
| `GET` | `/api/v1/health` | Saúde da aplicação |
| `GET` | `/` | Landing da plataforma |
| `GET` | `/login` | Login |
| `GET` | `/cadastro` | Cadastro |
| `GET` | `/loja/{slug}` | Loja pública (placeholder até a Fase 3) |
| `GET` | `/admin` | Painel do estabelecimento |
| `GET` | `/superadmin` | Painel da plataforma |

## Estrutura

```
backend/src/main/java/com/sacolao/
  config/          CORS, Security, Jackson, estáticos
  common/          API, exceções, health, páginas
  tenant/          TenantContext
  auth/            Fase 2
  security/        Fase 2
  establishment/   tenant
  user/            usuários e roles
  plan/            planos SaaS
  ...              demais domínios

frontend/
  css/             variáveis, layout, componentes, páginas
  js/api/          cliente HTTP
  js/pages/        scripts por tela
  pages/           HTML
```

## Fases

1. Fundação (esta) — projeto executável, schema base, frontend e health
2. Auth, JWT e isolamento multi-tenant
3. Categorias, produtos, loja e carrinho
4. Checkout, pedidos e dashboard
5. Pagamentos, webhooks e idempotência
6. Entrega, cupons, avaliações e estoque
7. Relatórios, auditoria e fiscal
8. Planos, assinaturas e SUPER ADMIN
9. Testes, segurança, otimização e produção
