# Local Supabase setup

How to run the local Supabase stack the PoC spike (#349) talks to. Everything here is
local-only: no Supabase account, no network, no real secrets.

## Prerequisites (one-time installs)

```bash
brew install --cask docker-desktop     # needs your password; launch Docker.app once after
brew install supabase/tap/supabase     # Supabase CLI
```

Docker Desktop must be running before `supabase start`.

## Repo layout

The CLI config is checked in:

- `supabase/config.toml` — created by `supabase init`, local stack configuration
- `supabase/migrations/20260610120000_workspaces.sql` — the `workspaces` table + RLS policy

Migrations are applied automatically when the stack starts (and on `supabase db reset`).

## Start / stop

```bash
supabase start    # first run downloads images (~2 GB); later runs take seconds
supabase status   # prints API URL, anon key, service_role key, Studio URL
supabase stop     # stops containers; data persists in a Docker volume
```

Or via make: `make supabase-start`, `make supabase-status`, `make supabase-stop`.

`supabase status` is the source of truth for local credentials. The API URL is
`http://127.0.0.1:54321`; Studio (web UI for poking at the database) runs on
`http://127.0.0.1:54323`.

Supabase CLI ≥ 2.x prints new-style keys: **Publishable** (`sb_publishable_…`, the
anon-key equivalent — what clients send as `apikey`) and **Secret** (`sb_secret_…`,
the service_role equivalent — admin use only). The local values are development
defaults shared by every local Supabase install — not secrets, safe to paste into
terminals and docs.

## Test user (one-time, after first start)

The spike signs in as a pre-created user. Create it via the local GoTrue admin endpoint
(take `SECRET_KEY` — the `sb_secret_…` value — from `supabase status`):

```bash
curl -X POST "http://127.0.0.1:54321/auth/v1/admin/users" \
  -H "apikey: $SECRET_KEY" \
  -H "Authorization: Bearer $SECRET_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@namdesktop.local","password":"namdesktop-local","email_confirm":true}'
```

## Environment variables the spike expects

| Variable | Local value |
|---|---|
| `SUPABASE_URL` | `http://127.0.0.1:54321` |
| `SUPABASE_ANON_KEY` | Publishable key (`sb_publishable_…`) from `supabase status` |
| `SUPABASE_TEST_EMAIL` | `test@namdesktop.local` |
| `SUPABASE_TEST_PASSWORD` | `namdesktop-local` |

Run the spike with `make spike` (see #349) with these exported, or inline:

```bash
SUPABASE_URL=http://127.0.0.1:54321 SUPABASE_ANON_KEY=... \
SUPABASE_TEST_EMAIL=test@namdesktop.local SUPABASE_TEST_PASSWORD=namdesktop-local \
  make spike
```

## Moving to hosted Supabase later

Nothing in the spike or future `CloudSyncService` code changes:

1. Create a hosted project at supabase.com, enable email/password auth.
2. Apply the same migration (`supabase db push`, or paste the SQL into the SQL editor).
3. Create the test/real user.
4. Point `SUPABASE_URL` and `SUPABASE_ANON_KEY` at the hosted project's values
   (Dashboard → Settings → API). Hosted keys are real credentials — never commit them.

## Troubleshooting

- `supabase start` hangs or errors → check Docker Desktop is actually running (`docker info`).
- Schema drifted while experimenting in Studio → `supabase db reset` re-applies migrations
  from scratch (wipes local data, including the test user — recreate it).
