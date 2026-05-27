# Cloud Sync (DB-backed)

> Feature design note — ready for implementation.
> Start with #215 (settings), then #216 (service), then #217 (UI).

## What and why

Add an optional cloud sync target backed by a hosted database. The user pushes and pulls
the workspace JSON to/from a remote endpoint. This complements the existing Git sync but
is designed as the stepping stone toward a future web API + web/mobile client.

Git sync was always slightly awkward — it requires a Git repo, treats a JSON blob as a
versioned text file, and has no programmatic access story. A DB-backed sync is cleaner,
enables conflict detection via timestamps, and gives the future web API a natural home.

## Strategic position

```
Desktop app  ──push/pull──▶  Cloud DB  (this feature)
                                 │
                          future web API
                           ┌─────┴─────┐
                        Web app    Mobile app
```

The desktop app talks directly to the cloud DB now. When the web API is built, the
desktop can switch to talking to the API instead — the `CloudSyncService` interface is
the seam that makes that swap cheap.

## Design decisions (settled)

**JSON blob, not relational schema — for now.** The workspace JSON stays as-is. The cloud
DB stores one row per workspace: `workspace_id, json, updated_at`. No domain model in
the DB yet. Schema migration to relational happens when the web API is built — that's
the forcing function that justifies it.

**Interface seam now.** `CloudSyncService` is an interface. The v1 implementation talks
directly to the DB HTTP API. When the web API arrives, a new implementation talks to
that instead. Desktop code doesn't change.

**Config is DB-agnostic.** `CloudSyncSettings` stores an endpoint URL + auth token, not
Turso-specific fields. Any HTTP-accessible service that accepts the schema works.

**Optimistic locking via `updated_at`.** On push, send the local `updated_at`; server
rejects (409) if remote is newer. Conflict shown to user as a simple Keep Remote / Keep
Local / Cancel dialog. For a personal single-user tool, last-write-wins with a warning
is the right call.

**Git sync stays.** `CloudSyncService` is an addition alongside `WorkspaceSyncService` /
`GitSyncService`. Nothing is removed. Users can run both or either.

## Open decision — which cloud DB?

**Must be resolved before starting #216.** Two candidates:

| | Turso | Supabase |
|---|---|---|
| Backend | Distributed SQLite (libSQL) | Postgres |
| API | HTTP (libSQL REST) | REST + realtime |
| Auth | Token (per-DB or per-user) | Anon key + JWT |
| Setup | Minimal — CLI + token | More config |
| Future multi-user | Limited | First-class (RLS, auth) |
| Dep overhead | None (raw HTTP) | None (raw HTTP) |

**Lean: Turso** for a personal single-user tool at this stage. Simple, lean, no backend
to run, token auth fits the AppSettings pattern already used by Git sync. Choose Supabase
if multi-user or realtime sync is on the medium-term roadmap.

Record the decision in issue #216 before closing it.

## The three issues

| # | Scope | Depends on |
|---|---|---|
| [#215](https://github.com/Aha43/NamDesktop/issues/215) | Settings: endpoint URL, token, workspace ID | — (do first) |
| [#216](https://github.com/Aha43/NamDesktop/issues/216) | CloudSyncService: push, pull, conflict detection | #215 |
| [#217](https://github.com/Aha43/NamDesktop/issues/217) | UI: toolbar push/pull, conflict resolution dialog | #215, #216 |

## Implementation sketch

### Schema (DB side)

```sql
CREATE TABLE workspaces (
    workspace_id  TEXT     PRIMARY KEY,
    json          TEXT     NOT NULL,
    updated_at    INTEGER  NOT NULL   -- epoch milliseconds
);
```

One row per workspace. `workspace_id` is a UUID generated on first enable, stored in
`CloudSyncSettings.workspaceId`.

### CloudSyncService interface

```java
package namdesktop.sync;

public interface CloudSyncService {
    PushResult push(NamWorkspace workspace, CloudSyncSettings settings);
    PullResult pull(CloudSyncSettings settings);
}
```

`PushResult` / `PullResult` are records carrying: `success: boolean`,
`remoteUpdatedAt: long`, and a `ConflictInfo` (null when no conflict).

### HTTP implementation

Plain `java.net.http.HttpClient` + Jackson. No new deps. Auth header:
`Authorization: Bearer <token>`.

Push: `PUT <endpointUrl>/workspaces/<workspaceId>` with body:
```json
{ "json": "<serialized workspace>", "updatedAt": 1234567890000, "ifNotNewerThan": 1234567880000 }
```
Server returns 409 with remote `updatedAt` if conflict. Implementation checks status
code and returns `PushResult` accordingly.

Pull: `GET <endpointUrl>/workspaces/<workspaceId>` — returns `{ "json": "...", "updatedAt": ... }`.

For Turso specifically: the HTTP API uses SQL over HTTP. The PUT/GET above would be
translated to `INSERT OR REPLACE` / `SELECT` SQL statements in the request body. See
Turso HTTP API docs for the exact envelope.

### Conflict handling

Local `updatedAt` is stored in AppSettings (updated after every successful push or pull).
On push conflict: show `CloudConflictDialog` — three buttons as described in #217.
"Keep remote" calls `pull` then saves locally. "Keep local" calls push with
`ifNotNewerThan` removed (force). "Cancel" is a no-op.

### Settings model

```java
public record CloudSyncSettings(
    boolean enabled,
    String endpointUrl,
    String authToken,       // never logged
    String workspaceId,     // UUID, generated on first enable
    long lastSyncedAt       // epoch ms, updated after push/pull
) {}
```

Persisted under `cloudSync` in AppSettings JSON. Absent → all defaults (disabled).

### Key files to read before starting

- `src/namdesktop/app/AppSettings.java` — add `CloudSyncSettings` here
- `src/namdesktop/ui/SettingsDialog.java` + `SettingsPanel.java` — add Cloud Sync tab
- `src/namdesktop/sync/GitSyncService.java` — HTTP client and push/pull pattern to follow
- `src/namdesktop/sync/WorkspaceSyncService.java` — existing sync interface for reference
- `src/namdesktop/ui/MainFrame.java` — where toolbar push/pull buttons go
