# Review Checklist

Used for every pull request. The reason it exists: an unreviewed change once deleted customer backups. Anything that deletes, overwrites or migrates data gets the closest look.

## Data safety

- [ ] Does the change delete, truncate, overwrite or migrate data? If yes, what protects existing rows, and how is it restored?
- [ ] Are destructive Flyway statements (`DROP`, `DELETE`, `TRUNCATE`, `ALTER ... DROP`) justified in the PR description?
- [ ] Is an already merged migration left untouched?
- [ ] Are scheduled jobs or cleanup code bounded by a clear condition and tested against it?

## Correctness

- [ ] Behavior matches the acceptance criteria of the linked issue.
- [ ] Status transitions are validated; illegal transitions are rejected.
- [ ] Failure paths are handled: a remote call fails, a timeout occurs, a duplicate request arrives.
- [ ] Multi-step flows compensate on failure (for example, a stock reservation is released if a later step fails).

## Tests

- [ ] New behavior has unit tests; endpoints and persistence have integration tests.
- [ ] Tests assert behavior, not implementation details.
- [ ] The failing case is tested, not only the happy path.
- [ ] CI is green.

## Security

- [ ] No secret, token, password or real personal data in code, config, tests or logs.
- [ ] Input is validated; errors do not leak stack traces or internals.
- [ ] No new public route bypasses the gateway API-key check.
- [ ] `/internal/**` is not routed by the gateway.

## Design and maintainability

- [ ] The service touches only its own database and talks to others over HTTP.
- [ ] Names explain intent; there are no code comments.
- [ ] No dead code, unused dependencies or commented-out blocks.

## Operations

- [ ] New configuration is an environment variable with a documented placeholder in `.env.example`.
- [ ] Health, logging and error responses stay consistent across services.
- [ ] README, API examples and decision records are updated.

## How to leave the review

At least one specific, line-level comment is mandatory, tied to a file and line, with a reason and, where useful, a suggested fix.

Good: "`ReservationService.java` line 48: if `release` throws after the courier call fails, the stock stays reserved. Wrap it in a catch that logs and retries once, and add a test for it."

Not enough: "looks good".

The review ends with an explicit decision:

| Decision | Meaning |
| --- | --- |
| Approve | Checklist walked through, nothing blocking |
| Request changes | At least one item above fails |
| Comment | Non-blocking remarks only |
