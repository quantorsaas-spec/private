# Quantor local run (authoritative)

## IMPORTANT
Each new archive is a full snapshot. Do **not** merge with older folders.
Delete the old project directory and extract the latest zip.

## Option A: Docker (recommended)
From repo root (`./`):

```bash
docker compose up -d --build
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

## Option B: Maven (developer run)
From repo root (`./`):

```bash
mvn -f quantor-api/pom.xml clean spring-boot:run -Dspring-boot.run.profiles=dev
```

## Local smoke test (JWT-protected API)

### 1) Register (or login) to get a token

```bash
export API=http://localhost:8080

TOKEN=$(curl -s -X POST "$API/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"Passw0rd!"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

echo "TOKEN=${TOKEN:0:24}..."
```

### 2) Start trading for current user

```bash
curl -s -X POST "$API/api/v1/trading/start" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc1"}'
```

### 3) Check status

```bash
curl -s "$API/api/v1/trading/status" -H "Authorization: Bearer $TOKEN"
```

### 4) Stop

```bash
curl -s -X POST "$API/api/v1/trading/stop" -H "Authorization: Bearer $TOKEN"
```

### 5) Subscription status

```bash
curl -s "$API/api/v1/billing/subscription" -H "Authorization: Bearer $TOKEN"
```
