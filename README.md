# Cliente Webhook - Loja

API de uma loja online que recebe os **avisos de pagamento** da plataforma de pagamento (projeto [`webhook`](https://github.com/erickpr9/webhook)) e libera os pedidos para envio.

É o outro lado do webhook. A plataforma recebe a confirmação do provedor (PIX, cartão) e **avisa a loja** com um POST assinado. A loja nunca precisa perguntar "esse pedido já foi pago?": ela só reage ao aviso.

```
Provedor ──webhook──▶ Plataforma (webhook, 8080) ──webhook de saída──▶ Loja (cliente-webhook, 8081)
                       inbox → worker → outbox                         valida → registra → libera o pedido
```

## O problema real que resolve

Uma loja só pode separar e enviar a mercadoria depois que o pagamento for confirmado. Se ela enviar antes, leva prejuízo. Se demorar, o cliente reclama. O aviso por webhook resolve isso na hora, mas traz riscos que esta API trata:

| Risco | Como a loja se protege |
|---|---|
| Alguém forja um aviso para liberar pedido sem pagar | Assinatura HMAC-SHA256 obrigatória, comparada em tempo constante, e janela de 5 minutos contra replay |
| A plataforma reenvia o mesmo aviso (timeout, retentativa) | O id do aviso é chave primária: a reentrega é reconhecida e não tem efeito |
| Dois reenvios chegam ao mesmo tempo | `INSERT ... ON CONFLICT DO NOTHING` na mesma transação; só um processa |
| Avisos chegam fora de ordem (recusa antiga depois da confirmação) | Aviso mais antigo que o último aplicado é descartado |
| Aviso com valor ou pagamento diferente do pedido | Registrado como `IGNORED` para análise manual, sem liberar o pedido |
| Falha no meio do processamento | Registro do aviso e mudança no pedido na mesma transação: ou tudo, ou nada |
| Tipo de aviso novo que a loja ainda não conhece | Registrado como `IGNORED`, sem erro |

### O que os códigos de resposta significam para a plataforma

A resposta da loja é o que decide o que a plataforma faz depois. Por isso a escolha de cada código é parte do contrato:

| Resposta da loja | Quando | O que a plataforma faz |
|---|---|---|
| `200` (`processed`, `ignored` ou `duplicate`) | Aviso registrado, com ou sem efeito | Marca como entregue |
| `400`, `401`, `410`, `413`, `415` | Aviso inválido, assinatura errada, recusa | Desiste na hora (`DEAD`): reenviar não resolveria |
| `5xx` ou timeout | Loja fora do ar, banco indisponível, conflito | Tenta de novo com backoff até a loja voltar |

Um aviso que não se aplica (por exemplo, fora de ordem) responde `200`, e não erro. Se respondesse erro, a plataforma reenviaria para sempre algo que nunca vai dar certo.

## Arquitetura

Mesma organização da plataforma: hexagonal, com a regra de dependência verificada por ArchUnit.

```
api ──▶ application ──▶ domain ◀── infrastructure
```

| Camada | Conteúdo |
|---|---|
| `domain` | `StoreOrder` e as transições de `FulfillmentStatus` |
| `application` | `ReceivePaymentNotificationUseCase` (idempotência, ordem, divergência), consultas, portas |
| `infrastructure` | JDBC (`NamedParameterJdbcTemplate`), transação, montagem dos casos de uso |
| `api` | Verificação HMAC, controllers, tratamento de erros, simulação de falhas |

Status do pedido na loja:

```
AWAITING_PAYMENT ──confirmado──▶ READY_TO_SHIP ──estornado──▶ REFUNDED
       │
       └─────recusado──────▶ PAYMENT_FAILED
```

A loja tem o **próprio banco** (Postgres na porta 5434). Ela não lê o banco da plataforma: a única integração entre os dois sistemas é o webhook.

## Como rodar

Pré-requisitos: Java 21 e Docker. Para o fluxo completo, a plataforma (projeto `webhook`) também precisa estar rodando no profile `local`.

1. Suba o banco da loja.

```powershell
docker compose up -d
```

2. Rode a loja no profile `local` (porta 8081). Esse profile liga a simulação de falhas e o Swagger.

```powershell
$env:SPRING_PROFILES_ACTIVE="local"; .\gradlew.bat bootRun
```

3. Rode a plataforma no profile `local`, no projeto `webhook`. Ela já aponta as notificações para `http://localhost:8081/webhooks/payment-notifications`, com o mesmo segredo.

O segredo HMAC é combinado entre os dois lados: `CLIENTE_WEBHOOK_SECRET` aqui e `WEBHOOK_NOTIFICATION_SECRET` na plataforma. No profile `local`, os dois usam o mesmo valor de desenvolvimento.

Rodando pela IDE no Windows, veja a seção sobre "Unable to establish loopback connection" no README da plataforma: vale o mesmo argumento de VM.

Se os acentos aparecerem quebrados no terminal (ex.: `expedi├º├úo`), rode `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` antes do `bootRun`. A explicação completa está no README da plataforma.

## Roteiro no Postman

Use as duas collections juntas: **Webhook Study - Payment Confirmation** (plataforma) e **Cliente Webhook - Loja** (esta, em [`postman/`](postman)). A collection da loja não precisa de environment próprio.

### 1. Caminho feliz

| # | Collection | Request | Esperado |
|---|---|---|---|
| 1 | Plataforma | **4. Simulação › Criar pedido** | `201` |
| 2 | Plataforma | **1. Webhooks › Pagamento confirmado** | `202` |
| 3 | Plataforma | **3. Admin › Listar notificações (entregas à loja)** (aguarde ~5s) | notificação `SENT` |
| 4 | Loja | **1. Loja › Avisos recebidos** | aviso `APPLIED`; guarda o `orderId` |
| 5 | Loja | **1. Loja › Consultar pedido** | `READY_TO_SHIP`, `readyToShip: true` |

No console da loja aparece `Order ... is now READY_TO_SHIP: released for picking and shipping`.

### 2. Loja fora do ar: a plataforma não perde o aviso

| # | Collection | Request | Esperado |
|---|---|---|---|
| 1 | Loja | **2. Simulação › Loja fora do ar (503)** | modo `UNAVAILABLE` |
| 2 | Plataforma | **Criar pedido** e depois **Pagamento confirmado** | `202` |
| 3 | Plataforma | **Listar notificações** (repita algumas vezes) | `RETRY_SCHEDULED`, `attempts` subindo, erro `503` |
| 4 | Loja | **2. Simulação › Voltar ao normal** | modo `NONE` |
| 5 | Plataforma | **Listar notificações** (aguarde a próxima tentativa) | `SENT` |
| 6 | Loja | **Avisos recebidos** e **Consultar pedido** | pedido `READY_TO_SHIP` |

As tentativas seguem o backoff de 30s, 2m, 10m e 1h. Para ver em segundos, suba a plataforma com backoff curto:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"; .\gradlew.bat bootRun --args="--webhook.worker.notifications.backoff=5s"
```

### 3. Loja lenta: processa, mas a plataforma não fica sabendo

| # | Collection | Request | Esperado |
|---|---|---|---|
| 1 | Loja | **Loja lenta (timeout)** | modo `SLOW` (10s por aviso) |
| 2 | Plataforma | **Criar pedido** e **Pagamento confirmado** | `202` |
| 3 | Plataforma | **Listar notificações** (após ~6s) | retentativa por `Read timed out` |
| 4 | Loja | **Consultar pedido** (após ~10s) | `READY_TO_SHIP`: a loja processou mesmo assim |
| 5 | Loja | **Voltar ao normal** | modo `NONE` |
| 6 | Plataforma | **Listar notificações** | `SENT` |

Na reentrega, o console da loja mostra `already received; ignoring redelivery`. É o cenário que justifica a idempotência: sem ela, o mesmo pedido seria liberado duas vezes.

### 4. Loja recusa: a plataforma desiste

| # | Collection | Request | Esperado |
|---|---|---|---|
| 1 | Loja | **Loja recusando (410)** | modo `REJECT` |
| 2 | Plataforma | **Criar pedido** e **Pagamento confirmado** | `202` |
| 3 | Plataforma | **Listar notificações** | `DEAD` com 1 tentativa e erro `410` |
| 4 | Loja | **Voltar ao normal** | modo `NONE` |

### 5. A loja sozinha

A pasta **3. Aviso manual (como a plataforma)** assina e envia um aviso direto para a loja, sem a plataforma. **Aviso com assinatura inválida (401)** mostra que um aviso forjado não libera nada.

## Endpoints

| Método e rota | Descrição | Disponível |
|---|---|---|
| `POST /webhooks/payment-notifications` | Recebe o aviso (`X-Event-Id`, `X-Timestamp`, `X-Signature`) | sempre |
| `GET /orders/{orderId}` | Situação do pedido na loja | sempre |
| `GET /notifications?limit=20` | Avisos recebidos, com resultado e motivo | sempre |
| `GET /simulation/failure-mode` | Modo de falha corrente | `cliente.simulation.enabled` |
| `PUT /simulation/failure-mode` | Troca o modo: `NONE`, `UNAVAILABLE`, `SLOW` ou `REJECT` | `cliente.simulation.enabled` |
| `GET /actuator/health` | Saúde da aplicação e do banco | sempre |

### Contrato do aviso

```http
POST /webhooks/payment-notifications
Content-Type: application/json
X-Event-Id: 0d8f6f5e-2c1b-4c55-9d1e-6a3b2c1d0e9f
X-Timestamp: 1795701791
X-Signature: sha256=<hex de HMAC-SHA256(secret, "{X-Timestamp}.{body}")>

{"id":"0d8f6f5e-2c1b-4c55-9d1e-6a3b2c1d0e9f","type":"payment.confirmed","occurred_at":"2026-11-26T14:03:11Z",
 "data":{"order_id":"ord_1042","payment_id":"pay_001","amount":15990,"currency":"BRL","payment_status":"CONFIRMED"}}
```

O `X-Event-Id` é o mesmo em todas as tentativas de um aviso. É ele que a loja usa para reconhecer reentregas.

## Testes

```powershell
.\gradlew.bat check
```

Roda 55 testes e exige cobertura mínima de 80% (JaCoCo). Os testes de integração usam Testcontainers, então o Docker precisa estar ativo.

| Tipo | Exemplos |
|---|---|
| Domínio | Transições de status, aviso fora de ordem, divergência de valor |
| Aplicação | Pedido criado no primeiro aviso, reentrega, tipo desconhecido |
| API e ponta a ponta | Assinatura, `200`/`400`/`401`/`413`/`415`, reentregas simultâneas, os modos de falha |
| Repositório | Lock otimista com Postgres real |
| Arquitetura | Regra de dependência entre camadas |

## Decisões

- **Processamento síncrono, na mesma transação do registro.** A loja só grava o aviso e atualiza um pedido, operações rápidas e locais. Se houvesse efeitos lentos ou externos (e-mail, ERP, transportadora), eles iriam para um outbox, como na plataforma.
- **Pedido criado no primeiro aviso.** Numa loja real o pedido nasce no checkout. Aqui os pedidos de exemplo nascem na plataforma, então a loja aceita criar o pedido a partir do aviso.
- **Simulação de falha em memória.** O modo volta para `NONE` ao reiniciar e só existe no profile `local`.
