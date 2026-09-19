# Wallet Ledger Service

## What it does

A double-entry wallet ledger exposed over HTTP. Accounts hold money in a single currency. Clients
create accounts, post transfers between them, read balances and read the per-account journal.

Every applied transfer writes exactly one debit and one credit under a single transfer identifier.
The debit and the credit commit together or not at all. The transfer endpoint takes a client-supplied
`Idempotency-Key`, so a retry - including one that arrives while the original request is still
executing - applies the transfer at most once and returns the original outcome.

All state lives in process memory behind outbound ports. There is no database, embedded or otherwise.
The ledger core is plain Java with no framework dependency and is exercised by tests that never start
Spring and never speak HTTP.

## Run

The single command that starts the service:

```bash
docker compose up --build
```

The container reports `healthy` once `/actuator/health/readiness` answers. The service then listens on
`http://localhost:8080`, and Swagger UI is at `http://localhost:8080/swagger-ui.html` with every
operation executable from the browser.

## API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/accounts` | Open an account with an optional non-negative opening balance |
| GET | `/api/v1/accounts/{accountId}/balance` | Read one balance |
| GET | `/api/v1/accounts/{accountId}/entries` | Read the journal of one account |
| POST | `/api/v1/transfers` | Post a transfer, header `Idempotency-Key` required |

The examples below are the ones that were executed against a running container, in order.

Open two accounts and keep their identifiers:

```bash
SOURCE_ID=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerReference":"customer-4711","currency":"EUR","initialBalance":"100.00"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accountId"])')

TARGET_ID=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerReference":"customer-4712","currency":"EUR","initialBalance":"0.00"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accountId"])')
```

Each creation answers `201`:

```json
{"accountId":"f1ba08a1-4c5f-4463-ac73-e236a346f91d","ownerReference":"customer-4711","currency":"EUR","balance":100.00}
```

Read a balance:

```bash
curl -s http://localhost:8080/api/v1/accounts/$SOURCE_ID/balance
```

```json
{"accountId":"f1ba08a1-4c5f-4463-ac73-e236a346f91d","currency":"EUR","balance":100.00,"observedAt":"2026-09-18T23:45:38.680137461Z"}
```

Post a transfer. The response carries `Idempotency-Replayed: false` the first time:

```bash
curl -s -D - -X POST http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k1' \
  -d "{\"sourceAccountId\":\"$SOURCE_ID\",\"targetAccountId\":\"$TARGET_ID\",\"amount\":\"25.00\",\"currency\":\"EUR\"}"
```

```
HTTP/1.1 201
Idempotency-Replayed: false
```
```json
{"transferId":"6ae0f182-7d26-402f-ac6a-604a508ab12b","status":"APPLIED","sourceAccountId":"f1ba08a1-4c5f-4463-ac73-e236a346f91d","targetAccountId":"8875d269-1cb5-4652-98b7-d5a8b5b4b151","amount":25.00,"currency":"EUR","postedAt":"2026-09-18T23:45:38.706450139Z","replayed":false}
```

Repeating that request verbatim returns `201` with the same `transferId`, `Idempotency-Replayed: true`
and no second movement of money:

```
HTTP/1.1 201
Idempotency-Replayed: true
```
```json
{"transferId":"6ae0f182-7d26-402f-ac6a-604a508ab12b","status":"APPLIED","sourceAccountId":"f1ba08a1-4c5f-4463-ac73-e236a346f91d","targetAccountId":"8875d269-1cb5-4652-98b7-d5a8b5b4b151","amount":25.00,"currency":"EUR","postedAt":"2026-09-18T23:45:38.706450139Z","replayed":true}
```

Reusing `k1` with a different amount is a conflict, not a silent replay:

```bash
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k1' \
  -d "{\"sourceAccountId\":\"$SOURCE_ID\",\"targetAccountId\":\"$TARGET_ID\",\"amount\":\"30.00\",\"currency\":\"EUR\"}"
```

```json
{"errorCode":"IDEMPOTENCY_KEY_REUSED","message":"idempotency key k1 was already used for a different request payload","details":[],"correlationId":"569c49d4-68b5-4fd4-b757-aea29c0f0c44","timestamp":"2026-09-18T23:45:38.745662018Z"}
```

Overdrawing is rejected with `422` and changes nothing:

```bash
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k2' \
  -d "{\"sourceAccountId\":\"$SOURCE_ID\",\"targetAccountId\":\"$TARGET_ID\",\"amount\":\"1000.00\",\"currency\":\"EUR\"}"
```

```json
{"errorCode":"INSUFFICIENT_FUNDS","message":"account f1ba08a1-4c5f-4463-ac73-e236a346f91d holds 75.00 EUR and cannot release 1000.00 EUR","details":[],"correlationId":"1574e93d-034f-4a6d-8d53-115cb90d7fd8","timestamp":"2026-09-18T23:45:48.029393754Z"}
```

A transfer to the same account is `422 SAME_ACCOUNT_TRANSFER`, an unknown account is
`404 ACCOUNT_NOT_FOUND`, and omitting the header is `400 VALIDATION_FAILED`:

```bash
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$SOURCE_ID\",\"targetAccountId\":\"$TARGET_ID\",\"amount\":\"5.00\",\"currency\":\"EUR\"}"
```

```json
{"errorCode":"VALIDATION_FAILED","message":"required header Idempotency-Key is absent","details":[],"correlationId":"5bba70d7-9f24-4d4c-9337-2c5c89d31eba","timestamp":"2026-09-18T23:45:48.079890497Z"}
```

Read the journal. The opening balance appears as a credit from the reserved external funding source,
followed by the debit of the applied transfer:

```bash
curl -s http://localhost:8080/api/v1/accounts/$SOURCE_ID/entries
```

```json
[{"entryId":"0890b02c-95b9-4866-95c1-df1b5186efa1:credit","transferId":"0890b02c-95b9-4866-95c1-df1b5186efa1","accountId":"f1ba08a1-4c5f-4463-ac73-e236a346f91d","direction":"CREDIT","amount":100.00,"currency":"EUR","postedAt":"2026-09-18T23:45:38.577754435Z"},{"entryId":"6ae0f182-7d26-402f-ac6a-604a508ab12b:debit","transferId":"6ae0f182-7d26-402f-ac6a-604a508ab12b","accountId":"f1ba08a1-4c5f-4463-ac73-e236a346f91d","direction":"DEBIT","amount":25.00,"currency":"EUR","postedAt":"2026-09-18T23:45:38.706450139Z"}]
```

Exceeding 100 requests per minute from one client address yields `429` with `Retry-After`:

```bash
for i in $(seq 1 120); do
  curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/accounts/$SOURCE_ID/balance
done | sort | uniq -c
```

```
  86 200
  34 429
```
```json
{"errorCode":"RATE_LIMIT_EXCEEDED","message":"request budget exhausted, retry after 31 seconds","details":[],"correlationId":"361f76e3-0bf2-4403-901b-b218d4fdb826","timestamp":"2026-09-18T23:46:06.977201217Z"}
```