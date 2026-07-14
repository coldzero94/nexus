---
paths:
  - "kotlin/**"
---

# Kotlin/Android Rules

- Stack: Kotlin 2.4.x + Compose, minSdk 34, Health Connect (connect-client 1.1.0), Room, Glance, WorkManager. Details: `docs/STACK.md`, layout: `docs/ARCHITECTURE.md`.
- AGP 9 has built-in Kotlin — never apply `org.jetbrains.kotlin.android`.
- `core/` is a KMP module (commonMain) — **no Android imports**; its iOS targets stay enabled so klib compilation catches contamination in CI. The XP formula exists only as pure functions in core.
- `RewardEvent` is an immutable ledger: never mutate records; corrections are appended compensating events.
- Changing the XP formula = one atomic set: bump formula version tag + update `docs/MVP.md §5` + update the case-table tests (spreadsheet parity, shared `balance/*.csv` fixtures).
- Read steps via `aggregate(COUNT_TOTAL)` — never `readRecords` for steps (double counting).
- Never hardcode the dataOrigin allowlist — remotely configurable, include `getCurrentDeviceDataSource()` alongside `"android"` (June 2026 SPN change).
- Never put health-derived values into analytics event payloads — events record occurrence only (allowlist enforced by tests).
- No hardcoded strings (resources, Korean default). No colors/dimensions outside design tokens.
- Every screen must work with Health Connect permissions denied (demo mode).

## Testing

- `core/` logic requires case-table tests (input → expected XP) — the same table as the balance spreadsheet.
- Seed synthetic data with Health Connect Toolbox on emulators. Mark items needing a physical Galaxy device with a `실기기` note in the issue.
