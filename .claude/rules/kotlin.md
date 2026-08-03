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
- **로딩 분기는 `ui/LoadingSkeletons.kt`의 탭별 스켈레톤을 쓴다**(#268) — 중앙 스피너·맨 텍스트 금지. 카드 수와 높이를 실제 콘텐츠와 맞춰야 완료 순간의 레이아웃 점프가 사라진다. 무한 애니메이션(shimmer)은 **화면당 하나**만 구동하고(`SkeletonScreen`), 리듀스드모션에서는 기동하지 않는다 — 무한 반복은 `motionDuration()` 스케일링으로 없앨 수 없다. 스켈레톤은 `clearAndSetSemantics`로 한 노드다.
- **`ConnectNotice`/`RetryNotice`/`FirstRunNotice` are already `NexusCard`s** — place them as siblings in the screen `Column`, never inside a section card. Nesting gives two same-colored cards and two `heading()` nodes, which reads as a rendering failure.
- List rows use `ui/NexusListRow` + `ui/NexusDividedList`. A right-aligned value column must stay in the **same merged semantics node** as its label (`docs/A11Y-TALKBACK.md`) — otherwise TalkBack reads the value last, with no antecedent.
- Every screen must work with Health Connect permissions denied (demo mode).
- **무한 `LaunchedEffect` 티커는 가시성으로 게이트한다** (#246): `LocalLifecycleOwner.current.lifecycle.currentStateAsState()`가 **STARTED** 미만이면 돌리지 않는다. 컴포지션은 앱을 내려도 살아 있어 티커가 백그라운드에서 계속 돈다(실측 15초당 CPU 36 jiffies → 0). 기준은 RESUMED가 아니라 STARTED다 — RESUMED는 '포커스까지 가진'이라 분할 화면·팝업 뷰에서 **보이는 채로** 애니메이션이 굳고(갤럭시가 타깃이다) 사용자에겐 죽은 펫으로 읽힌다. 절약분은 전부 STARTED 미만에 있다. `repeatOnLifecycle`은 블록을 `Dispatchers.Main.immediate`로 옮겨 컴포지션 컨텍스트를 벗어나므로 쓰지 않는다. 리듀스드모션(#228)과 **별개 트리거** — 둘 다 각각 작동해야 하고, 토글 순간 프레임 0 복귀까지가 #228의 계약이다.
- **`Resources.getIdentifier`(규약 기반 에셋 조회)는 결과를 메모한다** (#246). res id는 프로세스 내 불변이고, 설정 변경은 id가 아니라 로드 시점의 자원 선택을 바꾼다. 재컴포지션·프레임 루프 안에서 부르면 안 된다.
- **위젯 갱신은 그릴 게 바뀐 때만**(#246). 판정 키에 스냅샷 + *렌더 시점 시계로 파생되는 표시*를 함께 넣는다 — 스냅샷만 비교하면 원정 카운트다운이 얼어붙는다. 스킵에는 두 개의 안전장치가 필수다: ① "밀었음" 기록은 **푸시 뒤에** — 앞에 두면 실패한 푸시가 박제돼 영영 스킵된다 ② **시간 상한**(1시간) — `updateAll`은 Glance 세션 워커에 넘기고 즉시 반환하므로 성공 반환이 실제 렌더를 보장하지 않고, 거기서 실패하면 Glance가 자체 에러 레이아웃을 그린 뒤 삼킨다(`onCompositionError` 기본 구현).
- **렌더 결과 캐시는 완전한 결과만 담는다**(#246). 부분/실패 렌더를 캐시하면 한 번의 실패가 프로세스 수명 내내 굳는다 — 매번 다시 그리던 때는 다음 갱신이 저절로 복구했다. Glance 비트맵을 프로세스 수명 캐시에 담는 건 캐릭터 드로어블이 런타임 설정 비의존일 때만 성립한다(밀도 버킷은 무해 — 기기 고정 + 명시 크기 합성).
- **캐릭터 드로어블은 `tools/character_assets.py`가 생성한다**(#66) — 손으로 고치지 말고 생성기를 고칠 것. 몸을 한 번 정의하고 표정만 갈아끼워야 아홉 파일의 눈 위치·팔레트가 어긋나지 않는다. 에셋 추가는 **세 곳**이 맞물려야 한다: 드로어블 + `animations.json` 상태 + (표정이면) `mood_triggers.json`의 `face`. 하나라도 빠지면 크래시도 로그도 없이 기본 상태로 폴백한다(`MoodFaceAssetTest`가 고정).
- **Debug-only tooling goes in `app/src/debug/`, never behind `BuildConfig.DEBUG` in main** (#245). A `BuildConfig.DEBUG` branch still ships the code and its strings in the release APK — that put a "wipe the ledger" button in a reverse-engineerable form next to the crown jewel. Pattern: real implementation in `src/debug`, a same-signature no-op in `src/release`, and main calls it unconditionally. `DeveloperToolsGateTest` pins release absence (source scan — unit tests build the debug variant and can't inspect a release APK).
- Debug seeds write **synthetic ledger rows** (`synthetic-` key prefix), never real Health Connect records — a seeded record becomes an indistinguishable health-derived value in an append-only ledger.
- `RoomDatabase.clearAllTables()` is blocking and asserts off-main-thread — wrap it in `withContext(Dispatchers.IO)`. Never add a delete query to a DAO in main: that gives production code the ability to erase the ledger.

## Testing — 연출(모션·그리기) 검증

**이 하네스에서 무엇이 관측 가능한가** (#338). 이 표를 안 보고 쓰면 통과하지만 아무것도 검증하지 않는 테스트가 나온다 — #246·#268에서 실제로 아홉 번 그랬다.

| 관측 | 가능? | 비고 |
|---|---|---|
| 시맨틱 트리(존재·텍스트·contentDescription) | ✅ | 가장 믿을 수 있음 |
| 측정 크기(`size`) | ✅ | 단 `graphicsLayer`의 alpha·translation은 **크기를 안 바꾼다** |
| `positionInRoot` (`fetchSemanticsNode`) | ⚠️ | **`setContent` 직후 첫 프레임만** — 이후엔 낡은 값을 계속 준다 |
| `onGloballyPositioned` 궤적 | ⚠️ | 레이아웃이 다시 돌 때만 갱신 — 그리기 전용 변화는 못 봄 |
| alpha | ❌ | 시맨틱에 없음 |
| 그리기 전용 변화(shimmer·브러시) | ❌ | 크기·위치·시맨틱 모두 불변 |
| 컴포즈 노드 픽셀(`captureToImage`) | ❌ | `GraphicsMode.NATIVE`에서도 idle 미도달 → 타임아웃 |
| 무한 애니메이션 가동 여부(`waitForIdle` 타임아웃) | ❌ | 무한 전환이 돌아도 idle이 그냥 돌아온다 |
| 직접 만든 `Bitmap`의 픽셀 | ✅ | `@GraphicsMode(NATIVE)` + `getPixels` (위젯 합성 경로) |

**관측 불가일 때의 순서**: ① 그리기 결정을 **순수 함수로 끌어낸다**(`skeletonColors`·`skeletonBandLeft`·`celebrationEnter` 선례 — 값을 돌려주면 그냥 비교하면 된다) ② 그래도 안 되면 **소스 가드**로 삭제만 막고 한계를 주석에 적는다. 관측 가능한 척하는 단언은 없느니만 못하다.

**규율 3가지**
- **양성 대조 필수**: "안 움직인다"를 단언하면 같은 파일에 "평소엔 움직인다"를 함께 단언한다. 아무것도 안 그려도 통과하는 걸 막는다(`MotionHarnessGuardTest`가 강제).
- **`LocalMotionScale`은 `NexusTheme` 안쪽에서 주입**한다. 테마가 시스템 값으로 다시 공급하므로 바깥 주입은 조용히 무시된다(`MotionHarnessGuardTest`가 강제).
- **주기성을 가진 값은 주기와 어긋나게 센다**: 2프레임 루프에 짝수 틱을 흘리면 제자리로 돌아와 "멈췄다"가 통과한다.

**티커 `delay`는 컴포즈 프레임 클럭이 아니라 Robolectric 메인 루퍼가 굴린다** — `shadowOf(Looper.getMainLooper()).idleFor(...)`를 함께 부르지 않으면 티커가 선 채로 통과한다.

**컴포넌트가 아니라 배선을 검증한다**: "부를 수 있다"와 "부른다"는 다른 명제다. 화면이 실제로 그 컴포넌트를 쓰는지는 소스 가드로 고정한다(`SkeletonWiringTest` 선례) — #268은 기능 전체를 되돌려도 초록이었다.

## Testing

- `core/` logic requires case-table tests (input → expected XP) — the same table as the balance spreadsheet. **`core/src/jvmTest/resources/balance/*.csv` is the single source for balance tables** (spreadsheet export → commit CSV, no code edit; CI enforces parity via BalanceCsvHarnessTest).
- Seed synthetic data with Health Connect Toolbox on emulators. Mark items needing a physical Galaxy device with a `실기기` note in the issue.
