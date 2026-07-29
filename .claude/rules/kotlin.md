---
paths:
  - "kotlin/**"
---

# Kotlin/Android Rules

- Stack: Kotlin 2.4.x + Compose, minSdk 34, Health Connect (connect-client 1.1.0), Room, Glance, WorkManager. Details: `docs/STACK.md`, layout: `docs/ARCHITECTURE.md`.
- AGP 9 has built-in Kotlin — never apply `org.jetbrains.kotlin.android`.
- `core/` is a KMP module (commonMain) — **no Android imports**; its iOS targets stay enabled so klib compilation catches contamination in CI. The XP formula exists only as pure functions in core.
- `RewardEvent` is an immutable ledger: never mutate records; corrections are appended compensating events.
- **Bumping `@Database(version)` requires all three in the same PR** (#233): (a) the exported `app/schemas/<db>/N.json`, (b) a `Migration(N-1 → N)` registered via `addMigrations`, (c) a `MigrationTestHelper` test in `NexusDatabaseMigrationTest` that migrates a seeded v(N-1) DB and asserts the data survived. Missing any of them = block the PR. Alpha testers already have ledger rows; a missing migration hard-crashes their app at DB open and wipes their progress (a technical breach of "the character never regresses"). `fallbackToDestructiveMigration` is forbidden — it deletes the ledger.
- Changing the XP formula = one atomic set: bump formula version tag + update `docs/MVP.md §5` + update the case-table tests (spreadsheet parity, shared `balance/*.csv` fixtures) + **add a new `balance/frozen/v{N}/` directory** (#243).
- **Shipped formula versions are frozen**: `balance/frozen/v{N}/*.csv` records the output of a shipped `FORMULA_VERSION` and must never be edited or deleted — already-recorded ledger events are re-verified against it (BACKEND §1). If `FrozenFormulaVectorTest` fails, bump the version and add `v{N+1}/`; do not touch the existing files.
- Read steps via `aggregate(COUNT_TOTAL)` — never `readRecords` for steps (double counting).
- Never hardcode the dataOrigin allowlist — remotely configurable, include `getCurrentDeviceDataSource()` alongside `"android"` (June 2026 SPN change).
- Never put health-derived values into analytics event payloads — events record occurrence only (allowlist enforced by tests).
- No hardcoded strings (resources, Korean default). No colors/dimensions outside design tokens.
- **Colors come from tokens only** (#267): define in `ui/NexusColors.kt` (M3 scheme) or `ui/VizColors.kt`
  (data-viz palette) and reference by name. `Color(0x…)` anywhere else is rejected by
  `ColorTokenGuardTest` — a literal in a screen means dark theme and palette changes skip that one spot.
  Dimensions follow the same rule via `ui/NexusSpacing.kt` (not yet test-enforced).
- Every screen must work with Health Connect permissions denied (demo mode).

## Testing

- `core/` logic requires case-table tests (input → expected XP) — the same table as the balance spreadsheet. **`core/src/jvmTest/resources/balance/*.csv` is the single source for balance tables** (spreadsheet export → commit CSV, no code edit; CI enforces parity via BalanceCsvHarnessTest).
- Seed synthetic data with Health Connect Toolbox on emulators. Mark items needing a physical Galaxy device with a `실기기` note in the issue.
