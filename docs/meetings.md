# Sprint Plan and Stand-up Log

## What the CEO asks and the answer

What is done in two weeks: a working logistics platform. A customer creates a delivery order, stock is reserved, a courier is assigned, the courier moves the parcel through its statuses, and anyone can follow the parcel by tracking number.

Who does what: one engineer wearing five hats, tracked per issue by the `role:` label.

When it is ready: the sprint ends with a demo of the full flow started from a fresh clone with `docker compose up`.

## Sprint 1

| Item | Value |
| --- | --- |
| Length | 2 weeks, 2026-11-25 to 2026-12-08 |
| Goal | Order-to-delivery flow works end to end, runs in containers, is built and tested by CI, and is deployable to Kubernetes |
| Board | GitHub Project with columns Backlog, Ready, In progress, In review, Done |

## Labels

| Group | Labels |
| --- | --- |
| Type | `type:feature`, `type:bug`, `type:chore`, `type:docs`, `type:security` |
| Area | `area:order`, `area:warehouse`, `area:delivery`, `area:tracking`, `area:gateway`, `area:devops`, `area:docs` |
| Priority | `priority:P0`, `priority:P1`, `priority:P2` |
| Role | `role:tech-lead`, `role:product-owner`, `role:qa-review`, `role:devops`, `role:writer-security` |
| State | `status:blocked` |

## Backlog

| Id | Issue title | Labels | Week |
| --- | --- | --- | --- |
| B-01 | Protect main and add collaborators | `type:chore`, `area:devops`, `role:tech-lead`, `priority:P0` | 1 |
| B-02 | Team charter, access, decisions, security docs | `type:docs`, `area:docs`, `role:writer-security`, `priority:P0` | 1 |
| B-03 | PR template, issue templates, review checklist | `type:chore`, `area:docs`, `role:qa-review`, `priority:P0` | 1 |
| B-04 | Project board, labels and sprint plan | `type:chore`, `area:docs`, `role:product-owner`, `priority:P0` | 1 |
| B-05 | Maven multi-module skeleton with wrapper and run steps | `type:chore`, `area:devops`, `role:devops`, `priority:P0` | 1 |
| B-06 | warehouse-service: products, stock, reservations | `type:feature`, `area:warehouse`, `role:tech-lead`, `priority:P0` | 1 |
| B-07 | tracking-service: events and public timeline | `type:feature`, `area:tracking`, `role:tech-lead`, `priority:P0` | 1 |
| B-08 | delivery-service: couriers and delivery lifecycle | `type:feature`, `area:delivery`, `role:tech-lead`, `priority:P0` | 1 |
| B-09 | order-service: order flow with reservation and assignment | `type:feature`, `area:order`, `role:tech-lead`, `priority:P0` | 1 |
| B-10 | api-gateway: routing and API-key authentication | `type:feature`, `area:gateway`, `role:writer-security`, `priority:P0` | 2 |
| B-11 | Dockerfiles and Docker Compose | `type:chore`, `area:devops`, `role:devops`, `priority:P0` | 2 |
| B-12 | CI workflow: build, test, secret scan | `type:chore`, `area:devops`, `role:devops`, `priority:P0` | 2 |
| B-13 | CD workflow: build and publish images | `type:chore`, `area:devops`, `role:devops`, `priority:P1` | 2 |
| B-14 | Kubernetes manifests | `type:chore`, `area:devops`, `role:devops`, `priority:P1` | 2 |
| B-15 | End-to-end smoke test and final documentation | `type:chore`, `area:docs`, `role:qa-review`, `priority:P0` | 2 |

## Stand-up format

Ten minutes, three answers:

1. What was finished since the last stand-up.
2. What is started today.
3. What blocks progress.

Each stand-up adds one row to the log below.

## Stand-up log

| Date | Done | Today | Blockers |
| --- | --- | --- | --- |
