# Security

## Secret handling policy

- No password, API key, token, private key or certificate is committed.
- Runtime configuration comes from environment variables. `application.yml` files contain only `${VARIABLE:default}` references, and defaults are never real secrets.
- A `.env.example` file documents every variable with a placeholder. The real `.env` is git-ignored.
- Kubernetes secrets are created from a git-ignored file; only `secret.example.yaml` is committed.
- CI credentials are stored as GitHub Actions secrets and never printed in logs.
- If a secret is committed by mistake it is treated as leaked: rotate it first, then remove it from history.

## Controls and status

| Control | Where | Status |
| --- | --- | --- |
| `.gitignore` blocks `.env`, keys, certificates, build output | repository root | done |
| Pull request template asks for a no-secrets confirmation | `.github/PULL_REQUEST_TEMPLATE.md` | done |
| Review checklist has a security section | [review-checklist.md](review-checklist.md) | done |
| Branch protection on `main` | GitHub settings, [repo-setup.md](repo-setup.md) | to be applied by owner |
| GitHub secret scanning and push protection | GitHub settings | to be applied by owner |
| Dependabot for GitHub Actions | `.github/dependabot.yml` | done |
| Dependabot for Maven | `.github/dependabot.yml` | done |
| API key required at the gateway, fail-closed at startup, constant-time compare, key never forwarded or logged | api-gateway | done |
| `/internal/**` not routed by the gateway; path traversal and encoded-slash tricks rejected | api-gateway | done |
| Request size limit, secure response headers, request id on every response | api-gateway | done |
| Rate limiting of the public tracking endpoint | ingress | planned |
| Input validation on every request body | all services | in progress: order, warehouse, tracking, delivery done |
| Database credentials from environment only | all services | in progress: order, warehouse, tracking, delivery done |
| Recipient name, phone and address never leave delivery-service in events | delivery-service | done |
| Outgoing HTTP calls have connect and read timeouts | delivery-service | done |
| Courier capacity cannot be exceeded under concurrency | delivery-service | done |
| Public tracking timeline exposes no personal data or internal ids | tracking-service | done |
| Customer name, phone and address are sent only to delivery-service, never to tracking-service | order-service | done |
| Idempotency-Key makes order placement safe to retry | order-service | done |
| Failed order flows are compensated and retried until stock and courier are released | order-service | done |
| Tracking events are append-only | tracking-service | done |
| No delete endpoints for stock and reservations | warehouse-service | done |
| Database check constraints repeat business invariants | warehouse-service | done |
| Containers run as a non-root user | Dockerfile | done |
| Read-only root filesystem, all capabilities dropped, no-new-privileges, memory limits | docker-compose.yml | done |
| Only the gateway is published, bound to the loopback interface | docker-compose.yml | done |
| Compose refuses to start when a secret is missing; secrets are generated randomly into a git-ignored .env | scripts/init-env, docker-compose.yml | done |
| Each service database has its own role and password, no access for others | docker/postgres/init-databases.sh | done |
| Secret scan in CI (tracked .env files, gitleaks over history and tree) | GitHub Actions | done, first run pending |
| Dependency and image vulnerability scan in CI (Trivy, report only) | GitHub Actions | done, first run pending |
| Workflows use read-only permissions; publishing uses the built-in token only | GitHub Actions | done |
| Pull requests need build, secret-scan and compose-smoke to pass | GitHub settings, [repo-setup.md](repo-setup.md) | to be applied by owner |
| Restricted Pod Security Standard, non-root, read-only root filesystem, no capabilities, no service account token | `k8s/` | done, not yet applied to a cluster |
| Default-deny network policies with explicit allow rules; only the gateway is reachable from the ingress | `k8s/network-policies.yaml` | done, not yet applied to a cluster |
| Secrets created in the cluster from the git-ignored `.env`; CI rejects Secret objects in the manifests | `k8s/`, CI | done |
| CPU and memory requests and limits, probes, disruption budgets | `k8s/` | done |

## Reporting

A suspected leak or vulnerability is reported by a private message to the repository owner, not in a public issue.
