---
paths:
  - "supabase/**"
  - "kotlin/server/**"
---

# Backend Rules (Supabase + Ktor server)

- `supabase/` holds migrations only — **no Edge Functions** (server compute lives in `kotlin/server/` so the XP formula stays single-sourced in core). All schema changes via `supabase migration new`, never the dashboard.
- The Ktor server bypasses RLS — **every DB query must be scoped by the verified JWT `sub`**. Auth = Supabase JWKS asymmetric keys, new `sb_secret`/`sb_publishable` key system (legacy keys die end of 2026).
- `sb_secret`/DB password live only in deployment secrets — never in code, logs, or the client.
- DB access via Supavisor pooler (session 5432), pool size 3–5 per instance (free-tier connection limits). Cloud Run: `max-instances 1~2` + budget alerts.
- Server API changes regenerate `openapi/nexus-api.yaml` in the same PR (CI drift check). Server-side recomputation must pass the same `balance/*.csv` case tables as the app.
- Keep-alive: GitHub Actions cron hits the server's DB-touching healthcheck twice weekly (free tier pauses after 7 idle days; 90 days paused = unrecoverable).
