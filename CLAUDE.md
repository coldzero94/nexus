# NEXUS — Project Rules for Claude Code

Android life-RPG that turns health data (walking/running/strength) into 2D character growth. Two-person team (coldzero94, kimchi151). MVP is a local-only app with no server.

Monorepo layout (see `docs/ARCHITECTURE.md` for rationale):

- `kotlin/` — the Gradle root: `core/` (KMP game logic), `app/` (Android app), later `server/` (Ktor). **Run all Gradle commands from here.**
- `web/` — (S7~) Astro landing/policy + share pages, pnpm
- `supabase/` — (S9~) SQL migrations only
- `docs/` — planning & design, the source of truth

## Docs are the source of truth

Decisions and their rationale live in `docs/` (written in Korean), not in code. Check the relevant doc before working:

- `docs/MVP.md` — product spec (screens, XP formula, data model, monetization boundary)
- `docs/BACKLOG.md` — task definitions (E-IDs, acceptance criteria, size)
- `docs/SPRINTS.md` — sprint schedule, goals, gates
- `docs/STACK.md` — tech stack decisions and rationale (Android)
- `docs/ARCHITECTURE.md` — monorepo layout (android/ · web/ · supabase/), CI strategy
- `docs/BACKEND.md` — server-readiness contracts, backup, phase-2 architecture
- `docs/WORKFLOW.md` — collaboration process / `docs/RESEARCH.md`, `docs/BENCHMARK.md` — market evidence

**When a decision changes, update the affected doc in the same commit and record the rationale as a comment on the related issue.**

## Maintaining this file

- Update CLAUDE.md **in the same commit** whenever architecture materializes or changes: module added/renamed, build/test commands change, a new convention is adopted, or a rule below becomes stale.
- When project scaffolding lands (issue #5 / E1-1), fill in the "Build & commands" section below with real commands.
- If the user corrects the same mistake twice, add a specific rule here instead of relying on conversation.
- Keep this file under 200 lines. Prune rules that no longer apply; never keep two conflicting rules.
- **Scoped rules live in `.claude/rules/`** (one topic per file, `paths:` frontmatter): `kotlin.md` (kotlin/**), `web.md` (web/**), `backend.md` (supabase/**, kotlin/server/**). They load only when matching files are touched. Rules that apply to every session (project management, PR/commit, product invariants) stay in this file. New area → new rule file, not more lines here.

## Build & commands

All Gradle commands run from `kotlin/`. Requires JDK 21 and Android SDK (`kotlin/local.properties` → `sdk.dir`, not committed). Team OS mix: macOS (coldzero94, `JAVA_HOME=/opt/homebrew/opt/openjdk@21`), Windows (kimchi151 — use Android Studio's embedded JDK and `gradlew.bat`), Linux (CI). Line endings are enforced LF via `.gitattributes`. iOS-target tasks never run on Windows — CI covers the klib check.

- Build APK: `./gradlew :app:assembleDebug` (on this machine use `gradle`, not `./gradlew` — wrapper download is blocked by local TLS interception; CI uses the wrapper fine)
- Unit tests: `./gradlew :core:jvmTest` (run before every commit that touches `core/`) · app-layer fakes: `./gradlew :app:testDebugUnitTest` (HealthConnectSync/ExerciseRepository — run when touching `app/…/health/`)
- commonMain purity check: `./gradlew :core:compileKotlinIosArm64` (also runs in CI)
- Format: `./gradlew spotlessApply` before every commit. Static analysis: `./gradlew detekt` (Compose rules included). CI blocks on both `spotlessCheck` + `detekt`. Legacy findings live in `kotlin/detekt-baseline.xml` — shrink it, never grow it
- AGP 9 has built-in Kotlin — never apply `org.jetbrains.kotlin.android`

## Project management (GitHub)

- Every unit of work is an issue. When a new task is discovered: ① add it to `docs/BACKLOG.md` with an E-ID ② create an issue titled `[S#][E#-#] title` with acceptance criteria ③ assign the sprint milestone ④ add it to the project board.
- Sprint = milestone (S0–S4, S5+). Moving an issue between sprints = changing its milestone.
- Board (Projects #1) status is a kanban axis (Backlog → Ready → 진행 중 → 확인 대기 → Done), separate from sprint membership (= milestone). New issues start in **Backlog**. Only humans move items to `Ready` (during planning) — never move them yourself.
- Close issues via `Closes #N` in the PR body (keeps milestone progress accurate).
- If sprint scope or dates change, update `docs/SPRINTS.md` and the milestone due date together.
- Ideas and bugs go into issue templates (기능/버그), not chat.

## PR & commit rules

- **No code without a ticket**: every branch starts from an issue. If work appears mid-task, create the issue first.
- Branch naming: `feat|fix|docs|chore/<issue-number>-<slug>` (e.g., `feat/9-trust-filter`).
- Commit format: `type(scope)?: summary (#issue)` — types are `feat|fix|docs|refactor|test|chore|ci`, scope is a monorepo module (core/app/health/data/ui/web/supabase/server). Summary in Korean, no trailing period, ~50 chars, first line must stand alone. Optional body after a blank line explains what/why (never how). Formula-version or DB-schema changes use `feat!:` with the migration path in the body. Enforced by `.githooks/commit-msg` (`git config core.hooksPath .githooks`).
- Commit small and often on branches — main only keeps the squashed PR title.
- Every PR body must include a screenshot or screen recording, plus `Closes #N` (one issue per PR by default; list each if several). Merging then auto-closes the issue → board Done → milestone progress.
- Merges are **squash-only**, the PR title becomes the main commit (repo setting), and branches auto-delete.
- PR bodies must follow `.github/pull_request_template.md` — never skip the "확인 방법" section (reviews don't start without it). Update the description before merge if the code changed during review.
- Write PR titles/descriptions in product language (Korean, readable by a non-engineer).
- Never `git add -A` / `git add .` — stage explicit paths only. (A `git add -A` on a branch without .gitignore once committed 1,500 build-cache files to main.)
- No direct pushes to main for code — branch + PR. (Docs-only changes may commit to main directly.)
- Definition of done: not merged, but **verified on-device by the person who didn't build it**, with a confirming issue comment.

## Product invariants (never violate)

- No monetizable rewards (cash, tokens, trading) at any stage. (STEPN collapse; Korean game law §32)
- Never put health-derived values (step counts, workout duration, heart rate, XP numbers) into analytics/crash event payloads — events record occurrence only. (Google Play policy: "unintended receipt is still a violation")
- Raw health values never leave the device — backup/server payloads carry only computed XP and formula version.
- The character never dies or regresses. The only loss mechanic is the soft "condition" stat.
- Never promise "real-time" sync in UI or marketing (Samsung Health → Health Connect lags 30–60 min).
- Free-tier principle: before adopting any paid service/SDK, exhaust the free alternatives listed in `docs/STACK.md §8`.
