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

- Painel autenticado: o tenant sai do JWT e é confirmado no banco.
- Consultas operacionais usam `establishment_id`. Acesso cruzado responde **404**.
- `SUPER_ADMIN` não carrega tenant e não usa endpoints operacionais comuns.
- O frontend nunca é a fonte da verdade do isolamento.

### Autenticação

Spring Security + JWT HS256.

- Access token curto no header `Authorization: Bearer`
- Claims: `sub`, `role`, `establishmentId`
- Refresh token opaco, aleatório, persistido **somente como hash SHA-256**
- Rotação de refresh token a cada uso
- Cookie HttpOnly `folha_refresh` (SameSite=Lax, Path=`/api/v1/auth`) para o browser
- O JSON de login/refresh também devolve o refresh token para clientes de API/testes; o frontend **não** grava esse valor
- Access token no `sessionStorage` (exposto a XSS; TTL curto. Não usar `localStorage` para refresh)
- Recuperação de senha: a API responde sempre a mesma mensagem genérica e persiste só o hash do token. Envio de e-mail fica para uma fase posterior; no perfil `dev` o link `/redefinir-senha?token=...` aparece no log da aplicação.
- Senhas com BCrypt
- Roles: `SUPER_ADMIN`, `OWNER`, `ADMIN`, `STAFF`

CSRF do formulário está desabilitado porque o access token não vai em cookie. O refresh cookie só é enviado em POST same-site para `/api/v1/auth/*`.

## Tecnologias

- Java 21
- Spring Boot 4.1
- Spring Web MVC, Security, Data JPA, Validation
- Nimbus JWT
- PostgreSQL 16
- Flyway
- HTML5, CSS3, JavaScript (ES modules) e Fetch API

## Como executar localmente

### 1. PostgreSQL

Neste ambiente a porta `5432` já estava ocupada. O banco da Folha usa **5433**.

```
jdbc:postgresql://localhost:5433/folha
```

Usuário/senha padrão de desenvolvimento: `folha` / `folha`.

Com Docker (porta 5432 por padrão; ajuste se houver conflito):

```bash
docker compose up -d
```

### 2. Variáveis de ambiente

```bash
cp .env.example .env
```

Nunca commite o arquivo `.env`.

### 3. Subir a aplicação

```bash
./scripts/dev.sh
```

Ou:

```bash
cd backend
./mvnw spring-boot:run
```

Abra [http://localhost:8080](http://localhost:8080).

Saúde: [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health).

## Variáveis de ambiente

| Variável | Descrição |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` ou `prod` |
| `SERVER_PORT` | Porta HTTP (padrão `8080`) |
| `DATABASE_URL` | JDBC do PostgreSQL |
| `DATABASE_USERNAME` | Usuário do banco |
| `DATABASE_PASSWORD` | Senha do banco |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula |
| `JWT_SECRET` | Segredo HMAC do JWT (mínimo 32 bytes) |
| `JWT_ACCESS_EXPIRATION_MS` | TTL do access token (padrão 15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | TTL do refresh token (padrão 7 dias) |
| `PASSWORD_RESET_EXPIRATION_MS` | TTL do token de recuperação |
| `COOKIE_SECURE` | `true` em HTTPS |
| `BOOTSTRAP_SUPERADMIN_EMAIL` | Cria SUPER_ADMIN no perfil `dev` se ainda não existir |
| `BOOTSTRAP_SUPERADMIN_PASSWORD` | Senha do SUPER_ADMIN inicial |
| `FRONTEND_DIR` | Pasta do frontend em desenvolvimento |

## Banco e migrations

O Hibernate **não** cria tabelas. Toda mudança de schema passa pelo Flyway.

Fase 1 (`V1__baseline.sql`):

- `establishments`
- `users`
- `refresh_tokens`
- `password_reset_tokens`

A Fase 2 reutiliza esse schema. Nenhuma migration extra foi necessária.

IDs são UUID. Datas em `timestamptz` (UTC).

## Usuários iniciais

Não há seed obrigatório. O cadastro público cria um `OWNER` e o estabelecimento.

No perfil `dev`, um `SUPER_ADMIN` pode ser criado pelas variáveis `BOOTSTRAP_SUPERADMIN_*`.

## Endpoints principais

| Método | Caminho | Acesso |
| --- | --- | --- |
| `GET` | `/api/v1/health` | público |
| `POST` | `/api/v1/auth/register` | público |
| `POST` | `/api/v1/auth/login` | público |
| `POST` | `/api/v1/auth/refresh` | cookie ou body |
| `POST` | `/api/v1/auth/logout` | cookie ou body |
| `POST` | `/api/v1/auth/forgot-password` | público |
| `POST` | `/api/v1/auth/reset-password` | público |
| `POST` | `/api/v1/auth/change-password` | autenticado |
| `GET` | `/api/v1/auth/me` | autenticado |
| `GET` | `/api/v1/establishments/me` | OWNER, ADMIN, STAFF |
| `GET` | `/api/v1/establishments/{id}` | próprio tenant; SUPER_ADMIN (explícito) |
| `PUT` | `/api/v1/establishments/{id}` | OWNER, ADMIN do próprio tenant |
| `PATCH` | `/api/v1/establishments/{id}/status` | OWNER do próprio tenant ou SUPER_ADMIN |
| `GET/POST` | `/api/v1/users` | OWNER, ADMIN do próprio tenant |
| `GET` | `/api/v1/admin/establishments` | SUPER_ADMIN |

Páginas: `/`, `/login`, `/cadastro`, `/recuperar-senha`, `/redefinir-senha`, `/admin`, `/superadmin`, `/loja/{slug}`.

## Testes

Os testes de integração usam o banco `folha_test` na porta **5433**.

```bash
cd backend
./mvnw test
```

O teste mais importante é `TenantIsolationIT`: usuário do tenant A não lê nem altera dados do tenant B.

## Fases

1. Fundação — projeto executável, schema base, frontend e health
2. Auth, JWT e isolamento multi-tenant (esta)
3. Categorias, produtos, loja e carrinho
4. Checkout, pedidos e dashboard
5. Pagamentos, webhooks e idempotência
6. Entrega, cupons, avaliações e estoque
7. Relatórios, auditoria e fiscal
8. Planos, assinaturas e SUPER ADMIN
9. Testes, segurança, otimização e produção
