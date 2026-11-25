# api-gateway

Port 8080. The only service that is reachable from outside. It authenticates callers with an API key, routes public paths to the right service, and refuses everything else.

## Routes

| Path | Service | Authentication |
| --- | --- | --- |
| `/api/orders/**` | order-service | API key |
| `/api/warehouse/**` | warehouse-service | API key |
| `/api/delivery/**` | delivery-service | API key |
| `GET /api/tracking/{trackingNumber}` | tracking-service | none, public |
| any other `/api/tracking/**` | tracking-service | API key |
| `GET /actuator/health` | gateway itself | none |
| `/internal/**` | never routed | answers 404 |

Paths are forwarded unchanged. Unknown paths answer 401 without a key and 404 with a valid key. Only the `health` actuator endpoint is exposed.

## API key

Send `X-API-Key: <key>` with every protected call.

- Keys come from the environment variable `GATEWAY_API_KEYS`, several separated by commas. Several keys allow rotation: add the new key, move callers, then remove the old one.
- The gateway refuses to start when no key is set or a key is shorter than 16 characters.
- Keys are compared in constant time and never appear in logs, error messages or the properties `toString`.
- The header is removed before the request is forwarded, so services never see it.

Missing or wrong key:

```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: ApiKey header="X-API-Key"
Content-Type: application/problem+json

{"title":"Unauthorized","status":401,"detail":"A valid X-API-Key header is required"}
```

## Path guard

Every request path is checked before authentication or routing, because a path such as `/api/orders/../../internal/reservations` could otherwise be forwarded and normalized by the downstream server into an internal endpoint.

| Rejected with 400 | Example |
| --- | --- |
| Dot segments | `/api/orders/../x`, `/api/orders/./x` |
| Encoded dots, slashes, backslashes or NUL | `%2e%2e`, `%2f`, `%5c`, `%00` |
| Double encoding | `%252e%252e` |
| Empty segments | `/api//orders`, `//internal` |

Any path equal to or under `/internal`, in any letter case, answers 404 whether or not a key is sent.

## Request id

Each response carries `X-Request-Id`. A caller-supplied id is kept when it has 8 to 64 letters, digits or dashes; otherwise a new UUID is generated. The same id is forwarded to the service, including for 401, 400 and 404 answers, so a call can be followed across services.

## Limits and failure behavior

| Case | Result |
| --- | --- |
| Request body over 1 MB | 413 |
| Service refuses connection or host is unknown | 503 |
| Service connect or response timeout (2 s connect, 10 s response) | 504 |
| Routed responses | Carry `X-Content-Type-Options`, `X-Frame-Options` and the other `SecureHeaders` defaults |

A service outage never makes the gateway unhealthy: `/actuator/health` stays 200.

## Not done in this version

- Rate limiting of the public tracking endpoint. Tracking numbers are 10 random characters from a 32-symbol alphabet, which makes guessing impractical, but a limiter at the ingress is the next step.
- Per-user identity and roles. All callers holding a key have the same rights.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port |
| `GATEWAY_API_KEYS` | none, required | Comma-separated API keys |
| `ORDER_SERVICE_URL` | `http://localhost:8081` | order-service base URL |
| `WAREHOUSE_SERVICE_URL` | `http://localhost:8082` | warehouse-service base URL |
| `DELIVERY_SERVICE_URL` | `http://localhost:8083` | delivery-service base URL |
| `TRACKING_SERVICE_URL` | `http://localhost:8084` | tracking-service base URL |

## Running alone

```powershell
$env:GATEWAY_API_KEYS = "choose-a-local-key-of-16-or-more-characters"
.\mvnw.cmd -pl api-gateway spring-boot:run
```

Then:

```powershell
curl.exe -i http://localhost:8080/api/orders
curl.exe -i -H "X-API-Key: $env:GATEWAY_API_KEYS" http://localhost:8080/api/orders
```

The first call answers 401; the second reaches order-service, or answers 503 if it is not running.
