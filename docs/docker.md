# Running with Docker

One command starts PostgreSQL, the four services and the gateway.

## Requirements

- Docker Desktop (or Docker Engine) with Compose v2
- Nothing else: Java and Maven run inside the build image

## First run

From the repository root, in PowerShell:

```powershell
.\scripts\init-env.ps1
docker compose up --build -d
```

On Linux or macOS:

```
sh scripts/init-env.sh
docker compose up --build -d
```

`init-env` writes a git-ignored `.env` file with random database passwords and a random API key. It never overwrites an existing `.env` unless `-Force` (`--force`) is given. `.env.example` lists the variables for anyone who prefers to fill them by hand.

The first build downloads Maven and the dependencies and takes a few minutes; later builds reuse a cache.

Check that everything is healthy:

```powershell
docker compose ps
```

Call the API through the gateway, which listens only on `127.0.0.1:8080`:

```powershell
$key = ((Get-Content .env | Select-String '^GATEWAY_API_KEYS=').Line -split '=', 2)[1]
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i -H "X-API-Key: $key" http://localhost:8080/api/warehouse/products
```

Stop and keep the data, or stop and delete the data:

```powershell
docker compose down
docker compose down -v
```

## What is in the stack

| Container | Image | Reachable from the host |
| --- | --- | --- |
| postgres | `postgres:17` | no |
| warehouse-service, tracking-service, delivery-service, order-service | built from the root `Dockerfile` | no |
| api-gateway | built from the root `Dockerfile` | `127.0.0.1:8080` only |

Every service listens on 8080 inside the network and finds the others by container name. Start order is enforced with health checks: PostgreSQL, then warehouse and tracking, then delivery, then order, then the gateway.

## Image build

One `Dockerfile` builds any module; the service is chosen with the `SERVICE` build argument.

```
docker build --build-arg SERVICE=order-service -t logistics/order-service:dev .
```

- Stage 1 uses the Maven Wrapper in a JDK 21 image and packages only the chosen module and what it depends on. A BuildKit cache keeps downloaded dependencies between builds.
- Stage 2 is a JRE 21 image with only the jar, running as a non-root user (uid 10001).
- The JVM is told to use 75 percent of the container memory and to exit on an out-of-memory error so the container restarts cleanly.

## Hardening applied in Compose

- Containers run with a read-only root filesystem and a tmpfs for `/tmp`.
- All Linux capabilities are dropped and `no-new-privileges` is set.
- Each service has a 512 MB memory limit.
- Only the gateway publishes a port, and only on the loopback interface. To expose it on the network, change `127.0.0.1:8080:8080` in `docker-compose.yml` on purpose.
- Passwords and the API key come from `.env`; Compose refuses to start with a clear message when one is missing.

## Databases

A single PostgreSQL server hosts four databases, `orders`, `warehouse`, `delivery` and `tracking`. Each has its own role, its own password, and no access for anyone else (`REVOKE ALL ... FROM PUBLIC`). The roles and databases are created on the first start by `docker/postgres/init-databases.sh`; the script runs only when the data volume is empty. To change a password later, change it in the database and in `.env`, or recreate the volume with `docker compose down -v`.

Open a SQL shell:

```powershell
docker compose exec postgres psql -U postgres
```

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `required variable ... is missing a value` | `.env` does not exist; run `scripts\init-env.ps1` |
| `failed to connect to the docker API` | Docker Desktop is not running; start it and wait for the engine to be ready |
| A service stays `unhealthy` | `docker compose logs <service>`; most often a wrong password in `.env` after the volume was created with other values, fix by `docker compose down -v` |
| Port 8080 is busy | Stop the other program or change the host port in `docker-compose.yml` |
