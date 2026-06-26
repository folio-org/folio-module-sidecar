# Project Context

## Project Identity

| Field | Value | Source |
|---|---|---|
| Project name | folio-module-sidecar | pom.xml:6-8 |
| Repository/package | org.folio:folio-module-sidecar | pom.xml:5-7 |
| Project code/key | MODSIDECAR | branch `MODSIDECAR-198`, commit `1d32d20` (MODSIDECAR-182) |

## Work Item Tracker

| Field | Value |
|---|---|
| Provider | Jira (folio-org.atlassian.net) |
| Key/prefix | MODSIDECAR |

> Adapter configuration belongs exclusively in `## Ticket Adapter`. Do not duplicate adapter status or instructions in the Work Item Tracker table.

## Ticket Adapter

**Status**: configured
**Adapter**: Atlassian MCP server (`mcp__atlassian-mcp__*` tools; resolve `cloudId` once via `mcp__atlassian-mcp__getAccessibleAtlassianResources`)
**Lookup**: `mcp__atlassian-mcp__getJiraIssue` with `{cloudId, issueIdOrKey}` returns summary, description, status, and links; use `mcp__atlassian-mcp__searchJiraIssuesUsingJql` for queries
**Create**: `mcp__atlassian-mcp__createJiraIssue` with `{cloudId, projectKey: "MODSIDECAR", issueTypeName, summary, description}` passing the complete payload
**Output**: Jira issue key (e.g. `MODSIDECAR-198`) and browse URL `https://folio-org.atlassian.net/browse/<key>`

## Source Control And Review

| Field | Value |
|---|---|
| Provider | GitHub |
| Repository remote | git@github.com:folio-org/folio-module-sidecar.git |
| Default target branch | master |
| Review artifact type | PR |

## MR Adapter

**Status**: configured
**Adapter**: `gh` CLI (`gh pr create --base master`)
**Instructions**: Open the PR against `master`. PR title follows the commit convention (`<TICKET> - <Summary>`). PRs are squash-merged; the squash subject carries the `(#<pr-number>)` suffix automatically on merge.
