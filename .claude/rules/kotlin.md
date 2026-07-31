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
- Never hardcode the dataOrigin allowlist — merge the current device's on-device source at runtime (#205). **There is no `getCurrentDeviceDataSource()` API** in HC 1.1.0; derive it by matching a record's `Metadata.device` against `Build.MANUFACTURER`/`MODEL` with `type == TYPE_PHONE` (`DeviceIdentity` → `core/DeviceSourceResolver`). Observation can promote to tierB only — never tierA, and never past the manual-entry exclusion.
- Never put health-derived values into analytics event payloads — events record occurrence only (allowlist enforced by tests).
- No hardcoded strings (resources, Korean default). No colors/dimensions outside design tokens.
- **Colors come from tokens only** (#267): define in `ui/NexusColors.kt` (M3 scheme) or `ui/VizColors.kt`
  (data-viz palette) and reference by name. `Color(0x…)` anywhere else is rejected by
  `ColorTokenGuardTest` — a literal in a screen means dark theme and palette changes skip that one spot.
  Dimensions follow the same rule via `ui/NexusSpacing.kt`.
- **Vertical rhythm inside the 4 tabs comes from `Arrangement.spacedBy(NexusSpacing…)`, never a manual `Spacer`** (#260). `TabRhythmGuardTest` scans the whole `home`/`steps`/`growth`/`settings` packages — scanning only the root screen files missed a real case, because sections live in separate files here (#311). Full-screen scenes (onboarding, welcome-back, initial-level) are exempt and listed explicitly in the test.
- **`ConnectNotice`/`RetryNotice`/`FirstRunNotice` are already `NexusCard`s** — place them as siblings in the screen `Column`, never inside a section card. Nesting gives two same-colored cards and two `heading()` nodes, which reads as a rendering failure.
- List rows use `ui/NexusListRow` + `ui/NexusDividedList`. A right-aligned value column must stay in the **same merged semantics node** as its label (`docs/A11Y-TALKBACK.md`) — otherwise TalkBack reads the value last, with no antecedent.
- Every screen must work with Health Connect permissions denied (demo mode).
- **무한 `LaunchedEffect` 티커는 가시성으로 게이트한다** (#246): `LocalLifecycleOwner.current.lifecycle.currentStateAsState()`가 RESUMED 미만이면 돌리지 않는다. 컴포지션은 앱을 내려도 살아 있어 티커가 백그라운드에서 계속 돈다(실측 15초당 CPU 36 jiffies). `repeatOnLifecycle`은 블록을 `Dispatchers.Main.immediate`로 옮겨 컴포지션 컨텍스트를 벗어나므로 쓰지 않는다. 리듀스드모션(#228)과 **별개 트리거** — 둘 다 각각 작동해야 한다.
- **`Resources.getIdentifier`(규약 기반 에셋 조회)는 결과를 메모한다** (#246). res id는 프로세스 내 불변이고, 설정 변경은 id가 아니라 로드 시점의 자원 선택을 바꾼다. 재컴포지션·프레임 루프 안에서 부르면 안 된다.
- **위젯 갱신은 그릴 게 바뀐 때만**(#246). 판정 키에 스냅샷 + *렌더 시점 시계로 파생되는 표시*를 함께 넣는다 — 스냅샷만 비교하면 원정 카운트다운이 얼어붙는다. Glance 비트맵을 프로세스 수명 캐시에 담는 건 캐릭터 드로어블이 설정 비의존일 때만 성립한다.
- **Debug-only tooling goes in `app/src/debug/`, never behind `BuildConfig.DEBUG` in main** (#245). A `BuildConfig.DEBUG` branch still ships the code and its strings in the release APK — that put a "wipe the ledger" button in a reverse-engineerable form next to the crown jewel. Pattern: real implementation in `src/debug`, a same-signature no-op in `src/release`, and main calls it unconditionally. `DeveloperToolsGateTest` pins release absence (source scan — unit tests build the debug variant and can't inspect a release APK).
- Debug seeds write **synthetic ledger rows** (`synthetic-` key prefix), never real Health Connect records — a seeded record becomes an indistinguishable health-derived value in an append-only ledger.
- `RoomDatabase.clearAllTables()` is blocking and asserts off-main-thread — wrap it in `withContext(Dispatchers.IO)`. Never add a delete query to a DAO in main: that gives production code the ability to erase the ledger.

## Testing

- `core/` logic requires case-table tests (input → expected XP) — the same table as the balance spreadsheet. **`core/src/jvmTest/resources/balance/*.csv` is the single source for balance tables** (spreadsheet export → commit CSV, no code edit; CI enforces parity via BalanceCsvHarnessTest).
- Seed synthetic data with Health Connect Toolbox on emulators. Mark items needing a physical Galaxy device with a `실기기` note in the issue.
