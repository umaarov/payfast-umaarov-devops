# Repository Setup Runbook

Steps for the Tech Lead and Product Owner hats on GitHub. Each step names the evidence to keep for the audit.

## 1. Repository

- Repository: `umaarov/payfast-umaarov-devops`, visibility Private.
- Default branch: `main`.
- Settings, General, Pull Requests: allow squash merging only, default message "Pull request title and description", enable "Automatically delete head branches".

Evidence: Settings screenshot.

## 2. Collaborators

Settings, Collaborators, Add people. Add the teacher with Write permission. Record the login in [access.md](access.md), replacing `TEACHER_GITHUB_LOGIN`.

Evidence: collaborator list.

## 3. Protect main

Settings, Branches, Add branch ruleset (or classic rule) for `main`:

- Require a pull request before merging; required approvals 0 (see ADR-006).
- Require conversation resolution before merging.
- Require status checks to pass and select `build`, `secret-scan`, `kubernetes-manifests` and `compose-smoke` from the CI workflow. They appear in the list only after the workflow has run once.
- Require linear history.
- Block force pushes.
- Restrict deletions.

Evidence: ruleset page and a rejected direct push.

## 4. Security features

Settings, Code security: enable Dependabot alerts, Dependabot security updates, secret scanning and push protection.

Evidence: settings screenshot.

## 5. Labels

Create the labels listed in [meetings.md](meetings.md). With the GitHub CLI, one label looks like:

```
gh label create "area:warehouse" --color 1d76db --description "warehouse-service"
```

## 6. Project board

Projects, New project, Board layout. Columns: Backlog, Ready, In progress, In review, Done. Add the repository issues, enable the built-in workflow that moves closed issues to Done.

## 7. Issues

Create one issue per backlog row in [meetings.md](meetings.md) using the Task template, with assignee, labels, priority and sprint milestone "Sprint 1".

Evidence: board screenshot at the start and end of each week.
