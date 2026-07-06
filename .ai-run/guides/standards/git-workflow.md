# Git Workflow

Conventions derived from the repository's commit and branch history
(`git log --oneline`, `git branch -a`). Default target branch: `master`.

## Branch Naming Convention

Feature work uses the **Jira ticket key verbatim** as the branch name, optionally
prefixed with the author's handle:

- `MODSIDECAR-198` — ticket key only (current branch, dominant pattern)
- `pjacob/MODSIDECAR-194` — `<author>/<TICKET-KEY>`

Non-ticket or thematic work uses a `feature/<kebab-description>` form:

- `feature/multi-app-bootstrap-routing`

Always branch from an up-to-date `master`. One ticket → one branch.

## Commit Message Format

Two accepted styles coexist:

1. **Ticket-prefixed** (human feature work) — `MODSIDECAR-<NNN> - <Summary>` or
   `MODSIDECAR-<NNN>: <Summary>`:
   - `MODSIDECAR-182 - Migrate UMA Permission Checks to Response Mode Decision`
   - `MODSIDECAR-192: Adjust Keycloak error handling`
2. **Conventional Commits** (automation, deps, docs) — `<type>(<scope>): <summary>`:
   - `fix(deps): bump the prod-deps group ...`
   - `chore(deps-dev): bump the dev-deps group ...`
   - `docs: optimize AGENTS.md for conciseness`
   - `feat(logging): set minimum log level for sidecar to TRACE`

For SDLC Factory work in this repo, prefer the **ticket-prefixed** form
(`MODSIDECAR-<NNN> - <Summary>`) so the change is traceable to its Jira ticket.
The `(#<pr-number>)` suffix is appended automatically by GitHub on squash-merge —
do not add it manually.

## Merge Strategy

**Squash merge** via GitHub Pull Request. The history is linear: every mainline
commit corresponds to one squashed PR with a `(#NNN)` suffix and there are no
merge commits (`git log --merges` is empty). Rationale: one logical change = one
mainline commit, keeping `master` bisectable and readable.

## Anti-Patterns

| Bad | Good |
|---|---|
| `fixed stuff` | `MODSIDECAR-198 - Add desired-permissions header to egress request` |
| Branch `my-fix` | Branch `MODSIDECAR-198` |
| Committing directly to `master` | Open a PR from a ticket branch into `master` |
| Adding `(#123)` to a local commit by hand | Let GitHub add the PR-number suffix on squash-merge |
| Mixing two tickets in one branch | One ticket → one branch → one squashed PR |

## Troubleshooting

- **Wrong base / stale branch**: `git fetch origin && git rebase origin/master`
  (squash workflow keeps history linear; rebase rather than merge `master` in).
- **Branch already exists remotely with the same ticket key**: coordinate with the
  author (e.g. an existing `pjacob/MODSIDECAR-194`); prefix your handle to
  disambiguate.
- **Accidental commit on `master`**: create the ticket branch at HEAD
  (`git switch -c MODSIDECAR-<NNN>`), then reset `master` to `origin/master`.
