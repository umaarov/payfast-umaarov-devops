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
| API key required at the gateway | api-gateway | planned |
| `/internal/**` not routed by the gateway | api-gateway | planned |
| Input validation on every request body | all services | in progress: warehouse, tracking done |
| Database credentials from environment only | all services | in progress: warehouse, tracking done |
| Public tracking timeline exposes no personal data or internal ids | tracking-service | done |
| Tracking events are append-only | tracking-service | done |
| No delete endpoints for stock and reservations | warehouse-service | done |
| Database check constraints repeat business invariants | warehouse-service | done |
| Containers run as a non-root user | Dockerfiles | planned |
| Secret scan in CI | GitHub Actions | planned |
| Dependency vulnerability scan in CI | GitHub Actions | planned |
| Kubernetes Secrets and resource limits | `k8s/` | planned |

## Reporting

A suspected leak or vulnerability is reported by a private message to the repository owner, not in a public issue.
