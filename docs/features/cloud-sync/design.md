# Cloud Sync (Supabase)

> Feature design note — ready for implementation on the `experiment/cloud` branch.
> Start with #215 (settings), then #216 (service), then #217 (UI).
> Backend decision settled 2026-06-10 by the Supabase PoC (#348–#349): **Supabase**,
> local stack first. See `docs/features/supabase-poc/` for the validated groundwork.

## What and why

Add an optional cloud sync target backed by Supabase. The user pushes and pulls the
workspace JSON to/from the `workspaces` table. This complements the existing Git sync but
is designed as the stepping stone toward a future web API + web/mobile client.

Git sync was always slightly awkward — it requires a Git repo, treats a JSON blob as a
versioned text file, and has no programmatic access story. A DB-backed sync is cleaner,
enables real conflict detection, and gives the future web app a natural home.

## Strategic position

```
Desktop app  ──push/pull──▶  Supabase (local stack now, hosted later)
                                 │
                          future web app
```

The desktop talks directly to Supabase (GoTrue auth + PostgREST). Development runs
against the local stack (`supabase start`); moving to hosted is an env/settings swap —
same wire protocol, validated by the PoC spike (`namdesktop.spike.SupabaseSpike`,
`make spike`).

## Design decisions (settled)

**Supabase, not Turso.** Resolved by the PoC: plain `java.net.http.HttpClient` + Jackson
against GoTrue + PostgREST ran clean on the first attempt — see the decision checkpoint
comment on #349. RLS and first-class auth keep the future web app path open.

**JSON blob, not relational schema — for now.** The workspace JSON goes into the
`document` JSONB column of the `workspaces` table (migration
`supabase/migrations/20260610120000_workspaces.sql`), one row per (user, workspace name).
No domain model in the DB yet; relational migration happens if/when the web app forces it.

**Optimistic locking via `version`, not timestamps.** Push = PATCH guarded by
`version=eq.<expected>`, body bumps `version` by one. PostgREST returns 200 with **zero
rows updated** on a stale version — never a 409. Conflict detection counts returned rows
(`Prefer: return=representation`). Validated in the spike; this replaces the earlier
`updated_at` scheme.

**Auth: email + password in settings, fresh sign-in per operation.** `CloudSyncSettings`
stores the user's Supabase email and password (masked in the UI, plain in the local
settings JSON — accepted trade-off for a personal tool). Each push/pull signs in for a
fresh JWT (`POST /auth/v1/token?grant_type=password`). No session state, no token
refresh machinery — Supabase JWTs expire in ~1 h, and re-authenticating per click is
simpler than refresh logic. Revisit only if sync becomes high-frequency.

**Settings default to the local stack.** `supabaseUrl` defaults to
`http://127.0.0.1:54321` and `publishableKey` to the well-known local CLI key, so dev
sync works with zero config once `supabase start` is running. Hosted use = paste the
hosted URL + publishable key.

**Interface seam now.** `CloudSyncService` is an interface; the v1 implementation talks
PostgREST directly. A future web-API implementation swaps in behind the same seam.

**One toolbar button pair, active backend.** The existing push/pull toolbar buttons and
Cmd+S dispatch to cloud sync when it is enabled, otherwise to Git sync as today. If both
are configured, cloud wins. Git sync code stays untouched underneath.

**Conflict UX: Keep remote / Keep local / Cancel.** No three-way merge — for a
single-user personal tool, last-write-wins with an explicit warning is the right call.

**Startup auto-pull: deferred.** Manual push/pull ships first; auto-pull on launch
becomes a follow-up issue once conflict handling has been exercised by hand.

## Follow-up sprint: dev-mode cloud sync (planned 2026-06-10)

Dev mode currently disables sync entirely (`!devMode` guard) because both modes would
target the same remote row and clobber each other. The fix is the first concrete use of
the multi-workspace door the schema left open: dev mode syncs to its own row,
`name = 'dev'`, while normal mode keeps `name = 'default'`. Two rows, two independent
version watermarks, no collision possible.

What it takes:

1. **Service** — `SupabaseSyncService` takes the workspace name as a parameter instead
   of hardcoding `'default'`.
2. **Settings** — one `lastSyncedVersion` watermark per workspace name (e.g.
   `lastSyncedVersion` + `lastSyncedVersionDev`); a dev push must not move the default
   workspace's watermark.
3. **Migration** — add a unique index on `(owner_user_id, name)`; with two rows per user
   the key needs enforcing.
4. **UI** — drop `!devMode` from the *cloud* branch of `syncAvailable()` (Git sync stays
   dev-gated), route the name by mode, surface the target in tooltips/status messages
   ("Push workspace to Supabase (dev)").

Product angle: this turns dev mode into a durable, syncable sandbox — fool around freely
while the real inventory stays untouched. A possible "play mode" rebrand for end users
is noted in `docs/IDEAS.md`; naming is out of scope for this sprint.

## The three issues

| # | Scope | Depends on |
|---|---|---|
| [#215](https://github.com/Aha43/NamDesktop/issues/215) | Settings: enable, Supabase URL/key, email/password | — (do first) |
| [#216](https://github.com/Aha43/NamDesktop/issues/216) | CloudSyncService: push, pull, version-conflict detection | #215 |
| [#217](https://github.com/Aha43/NamDesktop/issues/217) | UI: toolbar dispatch, conflict resolution dialog | #215, #216 |

## Implementation sketch

### Schema (already applied)

The `workspaces` table from the PoC migration: `id uuid PK`, `owner_user_id uuid → auth.users`,
`name text default 'default'`, `version bigint default 1`, `document jsonb`,
`created_at` / `updated_at`. RLS: users see only their own rows. The desktop targets the
row where `owner_user_id = auth.uid()` and `name = 'default'` (multi-workspace later).

### CloudSyncService interface

```java
package namdesktop.sync;

public interface CloudSyncService {
    PushResult push(NamWorkspace workspace, CloudSyncSettings settings);
    PullResult pull(CloudSyncSettings settings);
}
```

`PushResult` / `PullResult` are records carrying success, the remote `version` on
success, and a conflict flag (push: stale version; pull: no remote row yet is *not* a
conflict — it's first-push state).

### Push flow

1. Sign in → JWT + user id.
2. Serialize workspace (existing `JsonWorkspaceRepository` logic) into `document`.
3. `PATCH /rest/v1/workspaces?owner_user_id=eq.<uid>&name=eq.default&version=eq.<local>`
   with `document` + `version = local + 1`, `Prefer: return=representation`.
4. 1 row returned → success, store new version locally. 0 rows → either no remote row
   (then `POST` insert, first push) or version conflict → CONFLICT result.

### Pull flow

1. Sign in → JWT.
2. `GET /rest/v1/workspaces?owner_user_id=eq.<uid>&name=eq.default&select=*`.
3. Row exists → deserialize `document` into the workspace, store remote `version` locally.
   No row → surface "nothing to pull".

### Conflict handling

Local `version` is stored in `CloudSyncSettings.lastSyncedVersion` (updated after every
successful push or pull). On push conflict: `CloudConflictDialog` — **Keep remote**
(pull + overwrite local, with data-loss warning), **Keep local** (re-read remote version,
push again with that as the guard — i.e. force), **Cancel** (no-op).

### Settings model

```java
public record CloudSyncSettings(
    boolean enabled,
    String supabaseUrl,        // default http://127.0.0.1:54321
    String publishableKey,     // default: local CLI well-known key
    String email,
    String password,           // masked in UI; plain in local settings JSON
    long lastSyncedVersion     // 0 = never synced
) {}
```

Persisted under `cloudSync` in AppSettings JSON. Absent → defaults (disabled, local URLs).

### Key files to read before starting

- `src/namdesktop/spike/SupabaseSpike.java` — the validated HTTP calls, headers, and
  conflict pattern; the service implementation is this, productionised
- `docs/features/supabase-poc/setup.md` — local stack, keys, test user
- `src/namdesktop/app/AppSettings.java` — add `CloudSyncSettings` here
- `src/namdesktop/ui/SettingsDialog.java` + `SettingsPanel.java` — Sync sidebar section
- `src/namdesktop/sync/GitSyncService.java` / `WorkspaceSyncService.java` — existing seam
- `src/namdesktop/ui/MainFrame.java` — toolbar push/pull buttons + Cmd+S dispatch

## Sprint-end checklist notes

- **Help**: Settings article gains the Cloud Sync group; likely a short "Cloud sync"
  concept article linked from Getting Started "What's next?".
- **MCP**: consider exposing sync status (enabled, last synced version) read-only.
- **e2e**: sync requires a running local stack — keep out of `e2e.json`; unit tests
  mock the HTTP layer instead.
