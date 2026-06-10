# Supabase PoC

> Status: **PoC sprint** — two issues. Goal is to answer one question: is Supabase a clean
> fit for NamDesktop cloud sync, with a path to a future web app? No app integration in
> this sprint. Decision checkpoint follows.

---

## Question being answered

Can a plain Java client authenticate with Supabase and push/pull the workspace JSON
document reliably, with no new dependencies, using only `java.net.http.HttpClient`
and Jackson?

## What this is not

- Not a cloud-sync implementation (that is #215–#217)
- Not a web app
- Not a backend service
- Not a production auth setup

## Architecture being validated

```
NamDesktop spike class
    │  POST /auth/v1/token   (email + password → JWT)
    ▼
Supabase Auth
    │  JWT
    ▼
Supabase PostgREST
    │  PATCH /rest/v1/workspaces?owner_user_id=eq.<uid>&version=eq.<v>
    │  GET   /rest/v1/workspaces?owner_user_id=eq.<uid>
    ▼
Postgres JSONB table
```

No custom backend. Desktop talks directly to Supabase via HTTP.

## Schema

```sql
create table workspaces (
  id             uuid        primary key default gen_random_uuid(),
  owner_user_id  uuid        not null references auth.users(id),
  name           text        not null default 'default',
  version        bigint      not null default 1,
  document       jsonb       not null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

alter table workspaces enable row level security;

create policy "Users own their workspaces"
  on workspaces for all
  using  (auth.uid() = owner_user_id)
  with check (auth.uid() = owner_user_id);
```

## Optimistic conflict detection

PostgREST does not return a 409 on version mismatch. The pattern is:

```
PATCH /rest/v1/workspaces?owner_user_id=eq.<uid>&version=eq.<expected_version>
```

If the version has changed, zero rows are updated. The spike checks the
`Content-Range` header or response body count and treats 0 rows updated as a conflict.

## Credentials / secrets

| Value | Where |
|---|---|
| Supabase project URL | env var `SUPABASE_URL` |
| Supabase anon key | env var `SUPABASE_ANON_KEY` |
| Test user email | env var `SUPABASE_TEST_EMAIL` |
| Test user password | env var `SUPABASE_TEST_PASSWORD` |

None of the above are committed. The docs record their names and purpose only.

## Decision checkpoint (after both issues are done)

Ask:

> Did the Supabase direct path feel clean from Java?

- **Yes** → proceed to implement `CloudSyncService` targeting Supabase (#215–#217 retargeted)
- **Mostly, but…** → record the friction and decide whether it is worth working around
- **No** → revisit Turso or a thin backend before committing

## Out of scope for this sprint

- App integration (no UI, no settings dialog, no toolbar button)
- Token refresh
- Multi-workspace support
- RLS beyond single-user ownership
- Any backend service (Hono, Javalin, etc.)
- Deployment

## Issues

| # | Scope |
|---|---|
| #348 | Supabase project setup and workspace table |
| #349 | Java spike: auth + workspace roundtrip |
