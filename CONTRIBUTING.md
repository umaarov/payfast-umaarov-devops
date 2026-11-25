# Contributing

## Workflow

1. Pick an issue from the board and move it to In progress.
2. Create a branch from an up-to-date `main`.
3. Commit in small steps.
4. Open a pull request with the template filled in; link the issue with `Closes #<number>`.
5. Get the review, resolve conversations, wait for green CI.
6. Squash-merge and delete the branch.

## Branch names

| Prefix | Use |
| --- | --- |
| `feature/` | New behavior, for example `feature/warehouse-reservations` |
| `fix/` | Bug fix |
| `chore/` | Build, tooling, refactoring |
| `docs/` | Documentation only |
| `ci/` | Workflows and pipelines |

## Commit messages

Conventional Commits: `type(scope): summary`, imperative mood, under 72 characters.

```
feat(warehouse): reserve stock for an order
fix(delivery): reject status change from DELIVERED
docs(security): list secret handling controls
ci: run tests on pull requests
```

Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `ci`, `build`.

## Pull requests

- One concern per pull request.
- The description explains why, how it was tested, and the rollback plan if data is affected.
- Tests ship in the same pull request as the code.
- No secrets, no commented-out code, no code comments.

## Local build

```
./mvnw -B verify
```

Run steps for the whole platform are in the README.

## Code style

- Formatting follows `.editorconfig`.
- Packages by feature inside each service: `api`, `domain`, `persistence`, `client`, `config`.
- Request and response objects are Java records with Bean Validation annotations.
- Errors are returned as RFC 7807 problem details.
