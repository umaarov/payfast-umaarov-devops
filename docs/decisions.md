# Architecture Decision Records

Format: context, decision, consequences. New decisions are appended; old ones are superseded, never rewritten.

## ADR-001 Service decomposition

Status: accepted

Context: The business domain has four capabilities with different change rates and data: orders, stock, couriers and parcel tracking. The assignment requires a microservice architecture.

Decision: Four services plus a gateway.

| Service | Owns |
| --- | --- |
| order-service | Orders, order items, order lifecycle, orchestration of the order flow |
| warehouse-service | Products, stock levels, reservations |
| delivery-service | Couriers, deliveries, assignment, delivery status |
| tracking-service | Tracking events, public timeline by tracking number |
| api-gateway | Routing, API-key authentication, request correlation |

Consequences: Each service can be built, tested, deployed and scaled alone. Cross-service workflows need explicit calls and failure handling.

## ADR-002 Java 21, Spring Boot 3.5, Maven multi-module

Status: accepted

Context: The course stack is Java and Spring Boot. Builds must be reproducible from a fresh clone.

Decision: Java 21 LTS, Spring Boot 3.5.x, Spring Cloud 2025.0.x for the gateway, one Maven multi-module build with a committed Maven Wrapper so no local Maven install is needed.

Consequences: One command builds every service. Shared dependency and plugin versions live in the parent POM. There is no shared domain library; services do not share classes.

## ADR-003 Database per service with Flyway

Status: accepted

Context: Services must be independently deployable and must not couple through a shared schema.

Decision: Each stateful service owns a separate PostgreSQL database. Schema changes are versioned Flyway migrations. Hibernate runs with `ddl-auto=validate`.

Consequences: No cross-service joins. Consistency across services is handled in the application flow. A migration is never edited after it has been merged.

## ADR-004 Synchronous REST between services

Status: accepted

Context: The order flow needs an immediate answer: is stock available, which courier is assigned. Message brokers add operational weight that the two-week scope cannot justify.

Decision: Services call each other over HTTP with timeouts. Endpoints meant only for other services live under `/internal/**` and are not routed by the gateway. Tracking events are written by order-service and delivery-service through `/internal/tracking-events`.

Consequences: Simple to run and debug. Calls can fail, so the order flow compensates: if reservation succeeds and a later step fails, the reservation is released. A broker can be introduced later behind the same interfaces.

## ADR-005 Authentication at the gateway with an API key

Status: accepted

Context: The audit asks who can call the system. A full identity provider is out of scope for this sprint.

Decision: The gateway requires an `X-API-Key` header on every public route except `GET /api/tracking/{trackingNumber}` and health endpoints. Keys are read from the environment variable `GATEWAY_API_KEYS`. Internal services are not exposed outside the cluster network.

Consequences: Coarse but real access control with no secret in the repository. Per-user identity and roles are a documented next step.

## ADR-006 Trunk protection for a single-maintainer repository

Status: accepted

Context: One engineer holds every role, and GitHub does not allow the author of a pull request to approve it. A rule that demands one approval would block every merge.

Decision: `main` requires a pull request, passing status checks and resolved conversations, blocks force pushes and deletions, and requires linear history. The required approval count is 0 while the teacher is a read-only reviewer; the review control is replaced by the mandatory review-checklist comment on every pull request. When a second maintainer is added, the required approval count is raised to 1.

Consequences: Every change is still visible, tested and commented before it lands. The weakness, that the author merges their own change, is stated openly and closed when a second maintainer exists.

## ADR-007 Squash merge and conventional commits

Status: accepted

Context: The history is read by auditors and must map each change to an issue.

Decision: Pull requests are squash-merged. Commit and PR titles follow Conventional Commits and reference the issue number.

Consequences: One commit per change on `main`, easy to revert and to trace.

## ADR-008 Stock reservation model

Status: accepted

Context: Orders must not promise stock that does not exist, even when many orders arrive at once, and a failed order must give its stock back.

Decision: Each product keeps `quantity_on_hand` and `quantity_reserved`. A reservation is one row per order (`order_id` is unique) with one line per product. Reserving runs a single conditional `UPDATE` per product that succeeds only if enough stock is available, inside one transaction, with skus processed in sorted order. A reservation ends as `RELEASED` (stock returned) or `COMMITTED` (stock leaves the warehouse). Both transitions are idempotent. Database check constraints repeat the invariants. No endpoint deletes data.

Consequences: No overselling under concurrency, no distributed lock, and repeated calls from a retrying order-service are safe. Stock history is not kept; an audit trail of movements would be a later addition.

## ADR-009 Integration tests on H2 in PostgreSQL mode

Status: accepted

Context: The development machine cannot always run Docker, and tests must pass from a fresh clone.

Decision: Integration tests run against in-memory H2 in PostgreSQL compatibility mode with the real Flyway migrations. Migrations use portable SQL only. Behavior on real PostgreSQL is verified by the Docker Compose end-to-end smoke test and by CI.

Consequences: Fast, dependency-free tests. A PostgreSQL-only difference can slip past unit-level tests, which is why the smoke test against PostgreSQL is mandatory before release.

## ADR-010 Append-only tracking events with caller-supplied event ids

Status: accepted

Context: Tracking is read by customers and written by two other services that retry on failure and may deliver events late.

Decision: A parcel's history is an append-only list of events. The caller supplies an `eventId`, and a repeated `eventId` returns the stored event instead of creating a second one. The timeline is ordered by occurrence time. The first event must be `CREATED`, and `DELIVERED` or `CANCELLED` closes the parcel. The public timeline exposes only status, description, location and time. Tracking numbers are `LG` plus 10 random characters, generated by order-service.

Consequences: Retries are safe and history cannot be rewritten. Two simultaneous `CREATED` events with different ids for one number could both pass the check; order-service generates the number and is the only writer of `CREATED`, so this is accepted for now.

## ADR-011 Transactional outbox for events leaving delivery-service

Status: accepted

Context: A courier's status change must update delivery-service, tracking-service and order-service. If the status were committed and a direct call to another service failed, the services would disagree, and a failed call must not block the courier from working.

Decision: Each status change and the events it causes are written in one local transaction (`outbox_events`). A scheduled dispatcher delivers them over HTTP in order per target. Transient failures keep the event pending; permanent client errors park it as `REJECTED` and are logged. Every event has its own `eventId` so receivers can deduplicate. Delivery-service assigns a courier synchronously when the delivery is created and fails with 409 if none has capacity, which keeps the order flow simple.

Consequences: Status changes survive outages of other services, and delivery is at-least-once. Other services see updates with a delay of a few seconds. The dispatcher polls the database; with several replicas, events may be sent twice, which receivers tolerate. A broker would replace the dispatcher without changing the outbox contract.
