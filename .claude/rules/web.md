---
paths:
  - "web/**"
---

# Web Rules (Astro on Cloudflare Workers)

- Share pages must render OG tags in the **initial HTML** — the Kakao scraper does not run JS. CI includes an OG smoke test.
- Game logic is never reimplemented in TypeScript — derived values (XP, level) come from server snapshots. If client-side execution is ever truly needed, the official path is adding a `js()` target to core.
- `assetlinks.json` lives at `web/public/.well-known/` — no redirects on that path, don't block it in robots.txt, SHA256 comes from Play App Signing (not the local keystore).
- Landing/policy pages are fully static; only share-snapshot routes are SSR.
