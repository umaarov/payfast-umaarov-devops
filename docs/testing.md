# Testing and Demo

## Test levels

| Level | Where | What it proves | Runs in |
| --- | --- | --- | --- |
| Unit and slice tests | Each service module | Domain rules, validation, error mapping, repositories on H2 in PostgreSQL mode (ADR-009) | `./mvnw verify`, CI job `build` |
| Service tests with fake neighbours | order-service, delivery-service, gateway | Failure handling when a neighbour is slow, down or answers 5xx: compensation, outbox retries, 503 and 504 mapping | `./mvnw verify` |
| Concurrency tests | warehouse-service, delivery-service | Stock and courier capacity are never oversold under simultaneous requests | `./mvnw verify` |
| Frontend tests | `frontend/` | Request wrapper, validation, delivery rules and every screen against a mocked `fetch` | `npm test`, CI job `frontend` |
| End-to-end smoke test | `scripts/smoke-test.sh` | The five real services work together through the gateway | CI job `compose-smoke` on real PostgreSQL; locally against any running stack |
| Manifest checks | `k8s/` | Manifests render, pass schema validation and contain no `Secret` | CI job `kubernetes-manifests` |
| Secret scan | whole history and working tree | No key or password was ever committed | CI job `secret-scan` |

Test counts at the end of Sprint 1: api-gateway 55, order-service 70, warehouse-service 28, delivery-service 49, tracking-service 18, 220 in total. The frontend adds 67 tests.

## End-to-end smoke test

`scripts/smoke-test.sh` talks to the gateway the way a real client does, and then to the frontend container. It reads the first key from `GATEWAY_API_KEYS` in the `.env` file at the repository root and uses a unique sku and courier phone per run, so it can be repeated on a running stack without cleaning up.

| Part | Checks |
| --- | --- |
| Gateway protection | Health and probe paths are public; missing and wrong keys give 401; `/internal/**` is not routed; path traversal gives 400 |
| Setup | Product and courier are created; duplicates give 409 |
| Order placed | Empty order gives 400; order is `CONFIRMED`; the same `Idempotency-Key` returns the same order; stock is reserved but still on hand |
| Public tracking | Timeline needs no key, contains `CREATED` and `COURIER_ASSIGNED`, and shows no customer name or address |
| Delivery lifecycle | Skipping a step gives 409; `PICKED_UP` moves the order to `IN_DELIVERY` and stock leaves the warehouse; cancelling then gives 409; `FAILED` needs a note; retry and `DELIVERED` move the order to `DELIVERED`; the timeline shows every step; a delivered parcel cannot move again |
| Rejection and cancel | Too much stock gives 409 `INSUFFICIENT_STOCK`; unknown sku gives 422; stock is untouched after rejections; cancelling releases the stock, closes the timeline with `CANCELLED`, and can be repeated |
| Web interface | Through the frontend container: the app and its fallback routes are served, a missing asset gives 404, the content security policy and other headers are set once, `/api` is proxied with and without a key, tracking works, the gateway health endpoint is not exposed, unsupported methods give 405 |

Events travel through the delivery outbox, so some checks wait up to 45 seconds and print `FAIL ... (timed out)` if the state never arrives.

### Run it

With Docker:

```powershell
.\scripts\init-env.ps1
docker compose up --build -d --wait
sh scripts/smoke-test.sh
docker compose down -v
```

`sh` comes with Git for Windows (Git Bash) or WSL. Against another gateway, set `GATEWAY_URL`; the frontend is expected at `FRONTEND_URL`, default `http://localhost:3000`. When nothing answers there the web interface part is skipped with a message, which is what the no-Docker run below does. Set `REQUIRE_UI=1` to make that an error, as CI does.

Without Docker, the five services can be started with in-memory databases for a quick check. In five terminals, from the repository root, with the test classpath so H2 is available:

```
./mvnw -pl warehouse-service spring-boot:run -Dspring-boot.run.useTestClasspath=true
```

Set `SERVER_PORT` (8082, 8084, 8083, 8081, 8080 for warehouse, tracking, delivery, order, gateway), `<NAME>_DB_URL=jdbc:h2:mem:<name>;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` and `<NAME>_DB_USER=sa` per service, and `GATEWAY_API_KEYS` for the gateway. Write the same key into `.env` as `GATEWAY_API_KEYS=...`. This only checks application behavior; PostgreSQL itself is covered by the CI job.

## What is not covered

- PostgreSQL-specific behavior outside CI: tests use H2 (ADR-009), and the first PostgreSQL run is `compose-smoke`.
- Kubernetes runtime behavior: probes, network policies and rolling updates have never run on a cluster.
- Load and performance: there are no load tests.
- Authorization beyond the shared API key: there are no user accounts or roles (ADR-013).

## Demo script for the sprint review

1. `git clone`, then `.\scripts\init-env.ps1`, then `docker compose up --build -d --wait`.
2. Create a product, then a courier, through the gateway (examples in each API document).
3. Place an order with an `Idempotency-Key`; show `CONFIRMED` and the reserved stock.
4. Open the public tracking URL without a key and show the timeline.
5. Move the delivery to `PICKED_UP`, `IN_TRANSIT`, `DELIVERED` and show the order and the timeline follow.
6. Place an order for more stock than exists and show the 409 and the untouched stock.
7. Show the green pull request checks and `docs/decisions.md`.
