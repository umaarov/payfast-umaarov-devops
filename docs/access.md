# Access and Change Approval

This document answers three audit questions: who has access, how a change is approved, and where passwords and keys are kept.

## Who has access

| Principal | GitHub login | Repository permission | Why |
| --- | --- | --- | --- |
| Owner, all five role hats | `umaarov` | Admin | Creates the repository, manages settings, performs the final merge |
| Teacher, reviewer and auditor | `TEACHER_GITHUB_LOGIN` | Write | Reads the code, leaves review comments, checks the history |

The repository is private. No deploy keys, outside collaborators, or public forks exist besides the principals listed above. Any new access is added in this table in the same pull request that justifies it.

## Access rules

- Principle of least privilege: new members start with Read or Triage and are raised to Write only when they own an issue.
- Admin is limited to the Tech Lead hat.
- Account security: two-factor authentication is required on every account that has access.
- Access is reviewed at the end of every sprint and removed when it is no longer needed.

## How a change is approved

1. A branch is created from `main` and linked to an issue.
2. A pull request is opened with the template filled in.
3. CI must pass: build, unit tests and integration tests.
4. The reviewer leaves at least one specific, line-level comment and states a decision, following [review-checklist.md](review-checklist.md).
5. All conversations are resolved.
6. The Tech Lead hat merges with a squash merge. The branch is deleted after merge.

`main` protection (see [repo-setup.md](repo-setup.md)): pull request required, required status checks, conversation resolution required, force pushes and deletions blocked, linear history enforced. CODEOWNERS routes `.github/`, `docs/`, `k8s/` and `scripts/` to the owner.

## Where secrets live

| Secret | Location | In the repository |
| --- | --- | --- |
| Gateway API key | Environment variable `GATEWAY_API_KEYS` | Never; only `.env.example` with a placeholder |
| Database passwords | Environment variables per service | Never; only `.env.example` with placeholders |
| CI credentials | GitHub Actions secrets | Never |
| Kubernetes secrets | Created in the cluster from a local file that is git-ignored | Only `secret.example.yaml` |

Details and controls are in [security.md](security.md).
