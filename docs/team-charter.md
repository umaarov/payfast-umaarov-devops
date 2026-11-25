# Team Charter

## Mission

Deliver a logistics and courier platform made of independent microservices (order, warehouse, delivery, tracking) behind one gateway, in a two-week sprint, with code that stays private, changes that are reviewed, and a pipeline that anyone can run with one command.

## Team model

One engineer (`umaarov`) holds all five roles. Roles are separate hats, not separate people: every issue, pull request and document names the hat it was done under, so an auditor can see which responsibility produced which artifact. When real team members join, each hat is handed to a named person without changing the process.

| Role | Responsibility | Artifacts |
| --- | --- | --- |
| Tech Lead | Repository, access, branch protection, final merge, conflict resolution | Protection rule, collaborator list, merges |
| Product Owner / Scrum Master | Backlog, labels, assignees, board, 10-minute stand-up | Issues, board, [meetings.md](meetings.md) |
| QA & Code Review Lead | PR template, review checklist, one concrete comment per PR, tests | Review comments, PR template, test results |
| DevOps / Platform Engineer | Application skeleton, `.gitignore`, run steps, Docker, CI/CD, Kubernetes | Dockerfiles, workflows, pipeline runs |
| Technical Writer & Security Champion | Charter, access and decision records, secret checks | README, [access.md](access.md), [decisions.md](decisions.md), [security.md](security.md) |

## Working agreements

1. `main` is protected. Nothing is pushed to it directly; every change arrives through a pull request.
2. Every pull request closes or references an issue and uses the pull request template.
3. Every pull request receives at least one specific, line-level review comment and an explicit decision (approve, request changes, comment), written against [review-checklist.md](review-checklist.md).
4. A pull request is merged only when CI is green and all conversations are resolved.
5. Secrets never enter the repository. Configuration comes from environment variables; examples live in `.env.example`.
6. Database schema changes are new Flyway migrations. An applied migration is never edited.
7. Services talk to each other only through their HTTP APIs, never through another service's database.
8. Endpoints under `/internal/**` are service-to-service only and are not routed by the gateway.
9. Source code contains no comments; names, small methods and tests carry the intent.
10. Stand-up is 10 minutes: done yesterday, planned today, blockers. It is logged in [meetings.md](meetings.md).

## Definition of ready

- Issue has a story, acceptance criteria, an owning role, a priority and an area label.
- Dependencies on other issues are linked.

## Definition of done

- Acceptance criteria are met and demonstrated.
- Unit and integration tests pass in CI.
- Pull request is reviewed under the checklist and merged by the Tech Lead hat.
- README, API examples and decision records are updated when behavior or architecture changed.
- No new secret, credential or real personal data is present in the diff.
- The change runs from a fresh clone with the documented commands.

## Conflict resolution

Disagreements on a pull request are settled in the pull request thread. If the thread does not converge, the Tech Lead hat decides and records the outcome as an entry in [decisions.md](decisions.md).
