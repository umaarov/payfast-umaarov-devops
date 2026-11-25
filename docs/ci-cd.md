# CI/CD

Two GitHub Actions workflows live in `.github/workflows`. Neither needs a secret that you create: publishing uses the `GITHUB_TOKEN` that GitHub gives every run.

## ci.yml

Runs on every pull request to `main`, on every push to `main`, and on demand.

| Job | What it does | Required for merge |
| --- | --- | --- |
| `build` | JDK 21, `./mvnw -B -ntp verify`: compiles all modules and runs all tests. Test reports are uploaded when it fails | yes |
| `secret-scan` | Fails if a `.env` file is tracked, then runs gitleaks over the full git history and over the working tree | yes |
| `kubernetes-manifests` | Renders `k8s/` with Kustomize, validates the result against the Kubernetes 1.31 schemas with kubeconform, rejects committed Secret objects, and checks that the two copies of the database init script are identical | yes |
| `compose-smoke` | Builds all five images, starts the whole stack with Docker Compose on real PostgreSQL, waits until every container is healthy, then runs `scripts/smoke-test.sh` against the gateway. Prints container logs when it fails | yes |
| `dependency-scan` | Trivy scans `pom.xml` dependencies and the Docker and Compose configuration for HIGH and CRITICAL findings that have a fix | no, report only |

`compose-smoke` also scans the `order-service` image with Trivy, report only. It is the first place where the code runs against PostgreSQL instead of the in-memory test database (ADR-009).

Workflow settings that matter:

- `permissions: contents: read` for the whole workflow; no job can write to the repository.
- A new push to a pull request cancels the older run of the same branch.
- Checkout does not keep credentials in the workspace.
- There are no path filters, so required checks never stay pending.

## cd.yml

Runs on every push to `main`, on tags that start with `v`, and on demand. For each of the five services it builds the image from the root `Dockerfile` and pushes it to GitHub Container Registry as `ghcr.io/<owner>/logistics-<service>`.

| Event | Image tags |
| --- | --- |
| Push to `main` | `main`, `sha-<short commit>`, `latest` |
| Tag `v1.2.3` | `1.2.3`, `sha-<short commit>` |

The job gets `packages: write` and nothing else beyond reading the code. Build layers are cached per service.

Cut a release after `main` is green:

```powershell
git switch main
git pull
git tag v0.1.0
git push origin v0.1.0
```

The images appear under the repository's Packages. They are private when the repository is private; to pull them from a cluster, create an image pull secret from a token with `read:packages`.

Deploying is a separate step: Kubernetes manifests reference these image names (see the Kubernetes document once it exists).

## Reading a failed run

| Failed job | Look at |
| --- | --- |
| `build` | The `Build and test all modules` log, and the `surefire-reports` artifact |
| `secret-scan` | The gitleaks output names the rule, the file and the commit; the secret itself is redacted. Rotate the secret first, then remove it |
| `compose-smoke` | `Show container logs after a failure`; a line starting with `FAIL` in the smoke test names the broken check |

## Running the same checks locally

```powershell
.\mvnw.cmd -B -ntp verify
.\scripts\init-env.ps1
docker compose up --build -d --wait
```

The smoke script is written for Linux shells and runs in CI. On Windows use Git Bash or WSL:

```
sh scripts/smoke-test.sh
```

## Updating the pipeline

- Actions are pinned to major versions. Dependabot opens a pull request when a new version is out.
- The gitleaks version and checksum verification are in the `Install gitleaks` step. To scan for another secret pattern, extend `.gitleaks.toml`; to ignore a dummy value, add its path to the allowlist there, with a reason in the pull request.
