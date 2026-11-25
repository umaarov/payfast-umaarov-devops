# Logistics and Courier Platform

Microservice platform for a logistics and courier company. Customers create delivery orders, the warehouse reserves stock, couriers are assigned and deliver, and parcels are tracked by tracking number.

Repository: `payfast-umaarov-devops`

## Services

| Service | Responsibility | Port |
| --- | --- | --- |
| api-gateway | Single public entry point, routing, API-key protection | 8080 |
| order-service | Order lifecycle and orchestration of the order flow | 8081 |
| warehouse-service | Inventory, stock reservations | 8082 |
| delivery-service | Couriers, delivery assignment and delivery status | 8083 |
| tracking-service | Tracking events and public parcel timeline | 8084 |

## Stack

- Java 21, Spring Boot 3.5, Spring Cloud Gateway
- PostgreSQL with one database per service, Flyway migrations
- Maven multi-module build
- Docker, Docker Compose, GitHub Actions, Kubernetes

## Documentation

| Document | Purpose |
| --- | --- |
| [docs/team-charter.md](docs/team-charter.md) | Roles, working agreements, definition of done |
| [docs/access.md](docs/access.md) | Who has access to what and how changes are approved |
| [docs/decisions.md](docs/decisions.md) | Architecture decision records |
| [docs/security.md](docs/security.md) | Secret handling and security controls |
| [docs/review-checklist.md](docs/review-checklist.md) | Checklist used for every pull request |
| [docs/meetings.md](docs/meetings.md) | Sprint plan and stand-up log |
| [docs/docker.md](docs/docker.md) | Running the whole platform with Docker Compose |
| [docs/api/gateway.md](docs/api/gateway.md) | api-gateway routes, API key, path guard, limits |
| [docs/api/order.md](docs/api/order.md) | order-service endpoints, order flow, compensation, configuration |
| [docs/api/warehouse.md](docs/api/warehouse.md) | warehouse-service endpoints, stock model, configuration |
| [docs/api/tracking.md](docs/api/tracking.md) | tracking-service endpoints, statuses, configuration |
| [docs/api/delivery.md](docs/api/delivery.md) | delivery-service endpoints, lifecycle, outbox events, configuration |
| [docs/repo-setup.md](docs/repo-setup.md) | GitHub settings runbook: protection, labels, board |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branching, commits, pull requests |

## Run steps

### Everything with Docker (one command after setup)

```powershell
.\scripts\init-env.ps1
docker compose up --build -d
```

On Linux or macOS use `sh scripts/init-env.sh`. The gateway listens on `http://localhost:8080`. Details, hardening and troubleshooting are in [docs/docker.md](docs/docker.md).

### Without Docker

Prerequisite: JDK 21. Maven is not required; the Maven Wrapper downloads it.

Build and test every service:

```
./mvnw -B verify
```

On Windows:

```
mvnw.cmd -B verify
```

Start one service (replace the module name):

```
./mvnw -pl warehouse-service spring-boot:run
```

Check that it is alive:

```
curl http://localhost:8082/actuator/health
```

Each service reads its port from `SERVER_PORT`; defaults are in the Services table.

api-gateway needs `GATEWAY_API_KEYS` (see [docs/api/gateway.md](docs/api/gateway.md)); it refuses to start without it. order-service, warehouse-service, tracking-service and delivery-service need PostgreSQL. Until Docker Compose is added, follow "Running alone" in [docs/api/order.md](docs/api/order.md), [docs/api/warehouse.md](docs/api/warehouse.md), [docs/api/tracking.md](docs/api/tracking.md) and [docs/api/delivery.md](docs/api/delivery.md). The tests do not need a database server.

### Troubleshooting on Windows

If startup or tests fail with `Unable to establish loopback connection`, the JVM cannot create its selector pipe in the temporary directory (seen when that path is a shortened 8.3 name). Point it at a plain ASCII folder once, then restart the terminal and the IDE:

```
mkdir C:\Users\Public\jtmp
setx JAVA_TOOL_OPTIONS "-Djdk.net.unixdomain.tmpdir=C:/Users/Public/jtmp"
```

## Project status

| Stage | Status |
| --- | --- |
| Repository, governance, documentation | done |
| Application skeleton | done |
| warehouse-service | done |
| tracking-service | done |
| delivery-service | done |
| order-service | done |
| api-gateway | done |
| Docker and Compose | files written, first run pending |
| CI/CD | planned |
| Kubernetes | planned |
