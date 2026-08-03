---
paths:
  - "web/**"
---

# Web Rules (Astro on Cloudflare Workers)

- Share pages must render OG tags in the **initial HTML** — the Kakao scraper does not run JS. CI includes an OG smoke test.
- Game logic is never reimplemented in TypeScript — derived values (XP, level) come from server snapshots. If client-side execution is ever truly needed, the official path is adding a `js()` target to core.
- `assetlinks.json` lives at `web/public/.well-known/` — no redirects on that path, don't block it in robots.txt, SHA256 comes from Play App Signing (not the local keystore).
- Landing/policy pages are fully static; only share-snapshot routes are SSR. Output stays in the default **directory** format (`/privacy/index.html`) — the policy URL is submitted to review, so it must not depend on how a host resolves extensionless paths.
- **개인정보처리방침은 앱 매니페스트가 진실이다** (#52). Play 심사가 이 페이지를 Data safety 폼·Health Connect 권한 화면과 대조하고, 어긋나면 Health apps declaration이 반려된다(1회당 최대 7일). 어긋나는 순간은 조용하다 — 앱에 권한을 더한 사람이 웹을 고칠 이유가 없고 페이지는 여전히 잘 뜬다. `scripts/check-policy.mjs`가 매니페스트의 건강 권한 전부가 페이지에 설명돼 있는지, 반대로 페이지에만 있는 유령 권한이 없는지 센다. 새 권한을 추가하면 `REQUIRED_PHRASE`와 페이지를 **같은 PR에서** 고칠 것.
- 게시 전에만 걸어야 하는 검사(자리표시자 등)는 `NEXUS_POLICY_PUBLISH=1` 뒤에 둔다 — 평소 CI에서 항상 실패하면 팀이 스크립트를 통째로 끈다.
