# NEXUS 기술 스택 v2 — Android 퍼스트

기준일 2026-07-14. **안드로이드 퍼스트 전환 결정 반영** — 6개 영역(Health Connect·Play 정책·Wear OS·앱 스택·CI/CD·분석)을 2026년 현재 기준으로 웹 검증한 결과. 전환 사유: 1호 실데이터 유저(치완)가 갤럭시 사용자, Wear OS는 진짜 워치페이스 캐릭터 가능, CI·배포 비용 우위. iOS는 게이트 통과 후 확장(이전 iOS 스택 검증 결과는 git 히스토리 `672e714`의 STACK.md 참고 — 그대로 유효).

## 결정 요약

| 영역 | 결정 | 한 줄 근거 |
|---|---|---|
| 언어·UI | Kotlin **2.4.x** + Jetpack Compose 1.11 (BOM) | 2.4.0이 2026-06 안정판. K2 기본 |
| minSdk | **34 (Android 14)** | Health Connect 프레임워크 내장(설치 마찰 0), 백그라운드 읽기가 14+ 전용, 한국 Android의 ~76.5%가 API 34+ |
| 데이터 소스 | **Health Connect** (connect-client 1.1.0 안정판) | 삼성헬스·갤럭시워치 데이터가 여기로 모임. Samsung Health Data SDK는 파트너십 심사 필요라 MVP 배제 |
| 로컬 DB | Room (+ WorkManager 2.11.2) | Glance 위젯이 앱 프로세스에서 실행되므로 멀티프로세스 불필요. Room도 KMP 지원이라 iOS 확장 시 락인 낮음 |
| 위젯 | Glance 1.2.0-rc01 — **홈 위젯 1차** | 잠금화면 위젯(Android 16 QPR2/One UI 8)은 갤럭시 실기기 검증 전까지 약속 금지 |
| 캐릭터 렌더링 | Compose 레이어드 Image + 프레임 티커, 위젯용은 비트맵 합성 → ImageProvider | 앱/위젯이 공유하는 단일 '캐릭터 컴포저' 모듈 |
| CI/CD | GitHub Actions (ubuntu) + Play App Signing | 리눅스 러너 1x 배율 — 월 2,000분 무료로 충분. 연 고정비 $0 (Play $25 일회성뿐) |
| 배포 | 개발 중: **GitHub Releases APK 자동 업로드** → Play 계정 후: **Play 내부 트랙** | 추가 서비스 0개(Firebase 불필요). 내부 트랙은 자동 업데이트 + 워치 앱 배포 경로 |
| 분석 | TelemetryDeck Kotlin SDK 7.x (무료 50k/월) | 리텐션·세션 빌트인, 식별자 해시. 대안 Aptabase |
| 크래시 | Sentry 무료(5k/월, tracing off, PII off) + Play vitals 보조 | vitals 단독은 초기 소규모에서 임계치 미달로 안 보임. **Crashlytics 배제**(동의 전 자동 수집) |
| 프로젝트 구성 | AGP 9.x + Gradle 9.6.x + JDK 17 + 버전 카탈로그 + build-logic 컨벤션 플러그인 | 2026-05 JetBrains 신 KMP 표준 구조 — [ARCHITECTURE.md](./ARCHITECTURE.md) |
| **서버 (S9~)** | **하이브리드**: Supabase(DB·Auth, 관리형) + **자체 Ktor 서버**(core 재사용, Docker→Cloud Run 서울 무료) | 산식 단일 진실 — Edge Functions(TS) 재구현 이원화 배제. 무상태 컨테이너라 운영 부담 최소 |
| **웹** | Astro + Cloudflare Workers 무료 — 랜딩·폴리시(정적) + 공유 스냅샷(SSR·OG) | KMP 웹 타깃 기각(CMP Web=Beta·SEO 불가). Vercel Hobby는 비상업 한정이라 기각 |
| **공유 전략** | **core = KMP 모듈**(`com.android.kotlin.multiplatform.library` 플러그인) — **iOS 타깃은 지금부터 켜되 iosApp은 게이트 후** | klib 컴파일은 Linux CI에서 가능 → macOS 러너 없이 commonMain의 Android 오염을 매 PR 차단. 구 플러그인 방식은 AGP 10에서 제거 예정 |

## 1. Health Connect 파이프라인 (검증 완료)

- **걸음**: `aggregate(StepsRecord.COUNT_TOTAL)` — readRecords로 직접 읽으면 이중 카운트. **운동 세션**: `ExerciseSessionRecord` (RUNNING/WALKING/STRENGTH_TRAINING/WEIGHTLIFTING/CALISTHENICS 등 타입 상수로 클래스 매핑 직접 가능), 세션 시간 범위로 HeartRateRecord 연계 조회.
- **수기 입력 필터**: `metadata.recordingMethod == RECORDING_METHOD_MANUAL_ENTRY` → XP 제외. SDK 1.1.0부터 기록 시 recordingMethod 의무화라 신규 데이터 신뢰도 높음. 단 서버측 필터는 dataOrigin만 — recordingMethod는 읽은 뒤 앱에서 거른다. 과거 데이터는 UNKNOWN 많음.
- **소스 신뢰 등급**: `dataOrigin`(패키지명, 위조 불가)으로 Tier 부여. **운동 세션에만 적용된다** — 걸음은 `aggregate(COUNT_TOTAL)`로 읽어 레코드별 provenance가 없어 등급을 매기지 않는다(수기 걸음만 `recordingMethod`로 따로 제외). 아래 SPN 대응도 세션 경로 얘기다 — 삼성헬스/온디바이스/자사 = 신뢰, 미상 서드파티 = 감점. ⚠️ **2026-06부터 온디바이스 걸음의 dataOrigin이 "android"에서 기기별 SPN으로 변경.**
  - **구현(#205, 2026-07-30)**: `getCurrentDeviceDataSource()` 같은 API는 **Health Connect 1.1.0에 없다**(실측 확인 — `Metadata`가 주는 건 `dataOrigin`과 `Device(type/manufacturer/model)`뿐). 그래서 **관측으로 판별한다**: 레코드의 `Device`가 `Build.MANUFACTURER`/`MODEL`과 일치하고 `type == TYPE_PHONE`이며 수기가 아니면, 그 레코드를 쓴 패키지를 현재 기기 온디바이스 소스로 보고 tierB에 런타임 병합한다(`DeviceIdentity` → `core/DeviceSourceResolver`).
  - 이걸 안 하면 사용자가 **자기 폰으로 자동 기록한 진짜 운동이 Tier C로 떨어져 XP에서 제외**된다. 사용자가 아무것도 잘못하지 않았는데 성장이 멈추고, 원인은 화면에 안 드러난다. **복구 창이 닫힌다**는 게 더 나쁘다: `grantSessions`는 미인정 세션에 행을 쓰지 않으므로 나중에 제대로 분류되면 같은 멱등성 키로 정상 지급되지만, 읽기 창(워커 7일·화면 28일)을 지나면 어느 창에도 안 들어와 영구히 지급되지 않는다.
  - **관측이 올릴 수 있는 상한은 B**다 — tierA(워치+심박)로는 올라가지 않고 수기 제외 필터도 뚫지 못한다. 다만 **그 상한이 지금 담아내는 건 리더보드 가중치(0.85)뿐**이다: 개인 XP는 A·B가 모두 100%이고 MVP가 지급하는 건 개인 XP뿐이라, C→B 승격은 사실상 0% → 100%다.
  - ⚠️ **`Metadata.device`는 쓰는 앱이 채우는 값**이다(HC가 호스트 기기와 대조하지 않는다). `dataOrigin`은 위조 불가지만 이 판별은 device에 의존하므로, HC 쓰기 권한을 받은 앱이 `Device(Build.MANUFACTURER, Build.MODEL, …)`을 실으면 자기 패키지를 tierB로 올릴 수 있다. 남는 제동은 일일 인정 상한(300)뿐이고, 이 방향의 과지급은 레코드 삭제 없이는 원장에서 되돌아가지 않는다. **받아들인 위험**이다 — 대안(진짜 운동을 C로 두기)이 더 나쁘고, 환금 보상이 없어 동기가 약하다. 리더보드(S9+)를 붙일 때 관측 승격 소스를 측정된 tierB보다 낮게 가중할지 결정해야 한다.
  - 화이트리스트는 하드코딩 금지(원격 구성 가능하게). 삼성헬스 패키지명은 실기기 실측으로 확정.
- **동기화**: 15분 주기 WorkManager + **Changes API 증분**(UpsertionChange/DeletionChange — 삭제가 보상 이벤트 트리거) + 앱 실행 시 즉시. 토큰 30일 만료 → 전체 재읽기 폴백 필수. 레이트 리밋(수치 비공개, 백그라운드 더 엄격) 때문에 폴링 남용 금지.
- **권한 3종**: 읽기 + `READ_HEALTH_DATA_IN_BACKGROUND`(위젯 자동 갱신) + `READ_HEALTH_DATA_HISTORY`(30일 초과 과거 → 온보딩 초기 레벨 부여). 셋 다 처음부터 선언 — 나중에 추가하면 재심사.
- **강도 지표**: HealthKit workoutEffortScore의 대응물 없음 → 세션 심박 시계열로 심박존 체류를 자체 산출(추정치임을 UI 표기).
- **삼성헬스 경로의 현실**: 워치→폰 삼성헬스→HC는 **30~60분 지연(추정) + 버전에 따른 동기화 버그 이력**(2026-07에도 운동 누락 보고). "운동 끝나자마자 반영"은 보장 불가 → 원정 연출("모험에서 돌아오는 중")로 UX 흡수. 사용자가 삼성헬스에서 HC 쓰기 권한을 켜야 함 — 온보딩에서 안내 필수. **S0에서 치완 실기기로 도달 시간·dataOrigin·recordingMethod 실측이 최우선 스파이크.**

## 2. Google Play 정책 — 타임라인 크리티컬 ⚠️

- **Play Console 개인 계정($25)은 늦어도 S6 시작 시 생성** — 신규 개인 계정의 "비공개 테스트 12명×14일" 시계는 S7 closed test 업로드부터 시작하므로 더 일찍 만들 실익이 없고, 그전 배포는 GitHub Release APK로 충분. 테스터 이탈 대비 **15명+ 풀**도 S6까지 확보.
- **Health apps declaration**: HC 데이터 타입별 선언·심사(≤7일, 거절 루프 사례 다수)가 **closed test 트랙에도 적용**. 거절 사유 1순위 = 과다 권한. 최소셋(Steps, ExerciseSession, Distance, TotalCaloriesBurned, HeartRate)만, 타입마다 "XP/능력치 변환에 필요" 정당화 — **'health-integrated games'가 공식 승인 유스케이스**라 게임화 목적을 숨기지 말고 명시. 카테고리는 'Activity and fitness'(Medical 아님), 스토어 문구에 질병·치료 표현 배제 + '의료기기 아님' 문구.
- **광고 전면 금지**(건강 데이터 보유 앱) → IAP 확정(이미 방침). **분석 이벤트에 건강 파생 수치(XP·레벨 수치 포함) 탑재 금지** — "SDK가 의도치 않게 수신해도 위반" 조항.
- **Data safety**: '수집' = 기기 밖 전송 기준 → 서버 없는 로컬 앱은 유리. 프라이버시 폴리시 URL 필수(스토어·HC 권한 화면 일치).
- **타임라인**: 개발 완료 → 공개 출시까지 최소 4주, HC 심사 거절 1회 가정 시 6주. 알파(릴리즈 APK/내부 트랙)는 이 게이트와 무관하게 즉시 가능.

## 3. Wear OS 캐릭터 — 3계층 (게이트 통과 후 구현, 설계는 지금)

| 계층 | 무엇 | 지원 범위 |
|---|---|---|
| 1 (워치 v1) | **WFF 워치페이스**: SequenceImages로 idle 애니메이션 + `[STEP_COUNT]` 태그로 걸음 반응 포즈 + 컴플리케이션 슬롯(자사 provider)으로 레벨/XP | Wear OS 4/5/6 전 기종. 시스템 렌더링이라 배터리 우위 |
| 2 (워치 v1) | 운동 모드 워치 앱: Health Services ExerciseClient(1Hz 심박) + Compose 풀 애니메이션. 폰↔워치는 DataClient(오프라인 버퍼링) | 운동 중 실시간 캐릭터 |
| 3 (v1.1+) | **Watch Face Push**: 레벨업/전직 시 진화된 워치페이스를 폰에서 생성·푸시 (Androidify 공식 패턴) | Wear OS 6+ (갤럭시워치4+ 2026-06 한국 롤아웃 완료). 앱당 1슬롯 제한 |

제약: WFF는 선언형이라 임의 앱 상태(XP·클래스) 직접 주입 불가 — 경로는 컴플리케이션 또는 Watch Face Push. AOD(항상 켜진 화면)는 1분 1회 갱신 + 85% 검정 → 정적 실루엣으로 타협. iOS(서드파티 워치페이스 전면 불가) 대비 명확한 우위이나 "워치페이스에서 풀 게임"은 아님 — 마케팅 문구 주의는 동일.

## 4. 캐릭터 렌더링

- 앱: `Box` 레이어드 Image(몸+표정+장비) + `LaunchedEffect` 프레임 티커(2~4프레임, 300~500ms), 비트맵 remember 캐시.
- 위젯: 레이어를 비트맵으로 합성해 `ImageProvider` 주입 — 앱/위젯 공유 '캐릭터 컴포저' 단일 모듈.
- 에셋: Aseprite 레이어별 PNG → 네이밍 `character_{state}_{frame}` + 애니메이션 JSON 메타데이터 (iOS 때 설계 그대로 유효).
- **렌더 낭비 3원칙 (#246)** — 저사양 갤럭시 A가 알파 base라 상시 낭비가 곧 배터리다.
  ① 이름→res id 조회(`getIdentifier`)는 이름당 1회로 메모하고, 상태의 프레임 id는 **진입 시 한 번에** 해석한다(프레임 루프 안에서는 인덱싱만).
  ② 티커는 화면이 **보일** 때만 돈다(lifecycle ≥ STARTED). RESUMED(포커스까지)로 재면 분할 화면에서 보이는 채로 캐릭터가 굳는다 — 절약분은 전부 STARTED 미만이라 낮춰도 잃는 게 없다. 리듀스드모션(#228)과 **별개 트리거**. 실측: 백그라운드 15초당 CPU 36 jiffies → 0.
  ③ 위젯은 **그릴 게 바뀐 때만** 갱신한다. 판정 키에 스냅샷뿐 아니라 *시각 파생 표시*(원정 잔여)를 함께 넣는다 — 스냅샷만 비교하면 진행 중 원정이 "약 N시간 남음"에 얼어붙고 개봉 대기 전환을 못 알린다. 스킵에는 안전장치 둘이 붙는다: 기록은 푸시 **뒤에**, 그리고 1시간 상한. `updateAll`은 Glance 세션 워커에 넘기고 즉시 반환하므로 성공 반환이 실제 렌더를 뜻하지 않고, 합성 실패는 Glance가 에러 레이아웃을 그린 뒤 삼킨다.
- 위젯 스프라이트 비트맵은 프로세스 수명 캐시다. 성립 전제는 **캐릭터 드로어블이 설정 비의존**(`drawable-night` 변형 없음)이라는 것 — 변형이 생기면 캐시 키에 설정을 넣어야 한다(테스트가 리소스 스캔으로 고정).

## 5. CI/CD·배포

- GitHub Actions: PR → lint+단위 테스트 / main 머지 → 서명 APK를 **GitHub Release로 자동 업로드**(추가 서비스 불필요) / Play 계정 생성 후엔 내부 트랙 업로드(`r0adkll/upload-google-play`)로 승격. gradle/actions v6 + configuration cache로 ~5분.
- Play App Signing(업로드 키 분실해도 리셋 가능 — 2인 팀에 결정적), 키스토어는 GitHub Secrets(base64), 커밋 금지.
- 계측(에뮬레이터) 테스트는 CI에서 제외(분수 폭증) — 로컬/야간.
- 워치 앱 배포는 Play 내부 트랙 또는 adb 사이드로드 (릴리즈 APK 직설치는 폰 전용).

**호스트측 테스트 하네스 (#232)**: 앱 레이어 테스트는 **Robolectric으로 JVM에서** 돈다 — 에뮬레이터 불요. CI가 이미 ubuntu에서 `:app:testDebugUnitTest`를 돌리므로 그대로 들어맞는다. 실 Room(`room-testing`)·WorkManager(`work-testing`)·Compose(`ui-test-junit4`)를 붙여, 손수 만든 페이크로 진짜 구현을 흉내 내던 한계(예: 유니크 제약 수동 재현)를 없앤다. `HostHarnessCanaryTest`가 이 셋의 배관을 지킨다 — 거기서 실패하면 앱 로직이 아니라 하네스가 깨진 것이다.

**커버리지는 아직 미가시화**: Kover 0.9.2가 AGP 9.2.1에서 Android variant를 인식하지 못해(리포트가 빈 채로 생성) 도입을 보류했다 — #296에서 재시도한다.
## 6. 개발 환경 (맥·윈도우 혼성 팀)

- **찬영 = 맥, 치완 = 윈도우** — 우리 스택은 윈도우 1급 지원(Android Studio·에뮬레이터·gradlew.bat·삼성 기기 드라이버). 우분투 전환 불필요. iOS 관련 태스크만 맥/CI 전담(역할 분담과 일치). 줄바꿈은 `.gitattributes`로 LF 강제.
- 치완 셋업 = Android Studio 설치(JDK 21 내장) + repo clone + `kotlin/local.properties`에 sdk.dir — 끝.
- API 34/36 에뮬레이터(HC 내장) + **Health Connect Toolbox**로 걸음·운동 세션 합성 데이터 주입. 디버그 빌드에 시드 데이터 버튼. 단위 테스트는 FakeHealthConnectClient.
- **Wear OS도 에뮬레이터 개발 가능**(android-wear 이미지, Health Services 합성 운동 데이터, 폰↔워치 AVD 페어링) — 단 삼성 레이어(삼성헬스 경로·One UI 배터리·잠금화면 위젯)는 에뮬레이터에 없음.
- 치완 갤럭시 실기기 전담 검증: 삼성헬스→HC 동기화 지연 분포, dataOrigin 실측, One UI 배터리 정책, (보유 시) 갤럭시워치 경로.

## 7. 프로젝트 구조

**모노레포 전체 구성은 [ARCHITECTURE.md](./ARCHITECTURE.md)가 단일 기준** — `kotlin/`(Gradle 루트: build-logic + core KMP + app + server) · `web/`(Astro) · `supabase/`(마이그레이션). `kotlin/` 내부 논리 모듈: core(XP 엔진·상태머신 — commonMain, android+ios+jvm 타깃), data(Room·원장), health(HC 어댑터), ui(토큰·캐릭터 컴포저), app(조립·Glance), server(S9~ Ktor).

의존 방향: `app → ui/data/health → core`. core는 치완 스프레드시트와 같은 케이스 테이블로 테스트하며, iOS 확장 시 무수정 재사용. 건강 어댑터는 인터페이스만 common에 두고 플랫폼별 구현(Health Connect/HealthKit). UI·위젯·워치·웹은 공유하지 않는다(리텐션 표면이 전부 플랫폼 전용 — Compose Multiplatform은 iOS·웹 모두 비채택, [ARCHITECTURE.md §3](./ARCHITECTURE.md)).

## 8. 비용 원칙 — 전부 무료 티어

**총 필수 지출 = Play Console $25 일회성. 그 외 전부 무료.**

| 항목 | 무료 범위 | 초과 시 대응 (트리거 명시) |
|---|---|---|
| GitHub Actions | 프라이빗 월 2,000분 (리눅스 1x) | 초과 상시화 → **리포 퍼블릭화 우선**(무제한 무료), 그다음이 과금($0.006/분) |
| GitHub Releases APK 배포 | 무료 | — |
| Play 내부/비공개 트랙 | 무료 (계정비 $25 일회성뿐) | — |
| TelemetryDeck | 월 50k 이벤트 | 초과 → 이벤트 다이어트 먼저, 그래도 부족하면 Aptabase 셀프호스트(무료)로 이전 (래퍼 뒤라 교체 쉬움) |
| Sentry | 월 5k 에러 | rate limit 설정으로 크래시 루프 방어. 초과 상시화면 vitals만으로 회귀 |
| 에뮬레이터·HC Toolbox | 무료 | — |
| 아트 | **무료 에셋·AI 생성 우선** | 구매는 최후 수단, 결정은 이슈 #1에서 |
| 2단계 서버 (게이트 후) | Supabase Free(50k MAU·500MB)로 PoC·초기 | 실사용자 증가 시에만 Pro $25/월 |
| iOS 확장 (게이트 후) | — | Apple Developer $99/년은 그 시점에 발생 |

원칙: 유료 전환은 "무료 한도 초과가 2개월 연속"일 때만 검토하고, 그 전에 무료 대안(퍼블릭화·셀프호스트·다이어트)을 먼저 소진한다.

## 9. 컨벤션·코드 품질

- **포맷팅**: ktlint(코틀린 공식 스타일) — Spotless로 실행, `.editorconfig`로 IDE 동기화. 로컬 `spotlessApply` 자동 수정 / CI `spotlessCheck` 차단 → 포맷 논쟁이 PR에서 사라짐.
- **정적 분석**: detekt(+Compose 룰셋) — 코드 스멜·복잡도·Compose 함정(remember 누락 등).
- **Android Lint (#242)**: CI가 PR마다 `:app:lintDebug`를 명시 실행하고, **`lintVital`(Play가 실제로 돌리는 검사)은 `checkReleaseBuilds` 기본값으로 `assembleRelease`에 자동 연결**돼 PR·main 양쪽에서 함께 돈다. 새 error는 빌드 실패(`abortOnError`는 AGP 기본값이며 `app/build.gradle.kts`에서 의도를 명시적으로 고정), warning은 보고만. 리포트는 CI 아티팩트 `lint-report`(lintDebug의 html·xml + lintVital의 txt).
- **lint baseline을 두지 않는다** — 도입 시점 error 0건이라 격리할 레거시가 없고, baseline이 있으면 새 error가 조용히 묻힌다(detekt-baseline과 다른 판단). **`updateLintBaseline` 계열 태스크를 실행하지 않는다** — 빨간 빌드는 그 자리에서 고치거나, 정당한 사유와 함께 해당 검사만 `disable`한다. AGP 업그레이드로 새 error 검사가 들어오면 §9의 "연 1회 마이그레이션 버퍼"에서 흡수한다.
- **적용 방식**: 루트 `kotlin/build.gradle.kts`에 일괄 적용(3모듈 규모에선 이게 단순 — #131). 모듈이 늘어 모듈별 설정이 필요해지면 `nexus.kotlin.quality` 컨벤션 플러그인으로 승격. detekt는 type resolution 없는 일괄 태스크라 TR 전용 룰은 미실행(detekt.yml 주석 참고).
- **아키텍처 규칙**: core 순수성은 KMP 타깃 분리가 컴파일러로 강제. Konsist(모듈 경계 유닛 테스트)는 보류 — 도입 트리거: 경계 위반이 리뷰에서 2회 잡히면.
- 상태: Compose + ViewModel(+ Flow). 테스트: JUnit5 + core는 케이스 테이블 기반.
- 문자열: 리소스 externalize, ko 기본. 분석 이벤트 allowlist를 코드로 강제(§2 정책).
- 연 1회(가을) AGP/Gradle 마이그레이션 버퍼.

## 10. 릴리스 서명·버전 코드 (#230)

**키스토어는 절대 커밋하지 않는다** (`.gitignore`: `*.jks`·`*.keystore`·`kotlin/keystore.properties`).

- **업로드 키 배관**: `app/build.gradle.kts`가 gradle property 4종(`nexus.upload.storeFile`/`storePassword`/`keyAlias`/`keyPassword`)을 읽어 `signingConfigs["upload"]`를 만든다. **넷 중 하나라도 없으면 signingConfig를 만들지 않고 release가 debug 키로 폴백** — 로컬·포크 PR이 서명 없이도 빌드된다. 폴백 APK는 Play 업로드 불가이고, 기존 릴리스 빌드를 인플레이스 업데이트하지도 못한다.
- **CI 배관**: main push에서만 동작. `NEXUS_UPLOAD_KEYSTORE`(키스토어 base64) Secret을 `kotlin/upload.jks`로 복원하고 비밀번호 3종(`NEXUS_UPLOAD_STORE_PASSWORD`·`NEXUS_UPLOAD_KEY_ALIAS`·`NEXUS_UPLOAD_KEY_PASSWORD`)을 property로 넘긴다. Secret 미설정이면 복원 스텝을 건너뛰고 GitHub Release 노트에 "debug 키 서명" 경고가 붙는다.
- **versionCode**: `-Pnexus.versionCode=<github.run_number>`로 주입(로컬 기본 1). Play 내부 트랙은 같은 versionCode 재업로드를 거부하므로 빌드마다 증가해야 한다.
- **키스토어 생성**(1회, 사람):
  ```
  keytool -genkeypair -v -keystore upload.jks -alias nexusupload \
    -keyalg RSA -keysize 2048 -validity 10000
  base64 -i upload.jks | pbcopy   # → GitHub Secret NEXUS_UPLOAD_KEYSTORE
  ```
  Windows(PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) | Set-Clipboard`.
  원본 `.jks`와 비밀번호는 팀 비밀번호 관리자에 보관 — 레포·이슈·채팅에 올리지 않는다.
- **로컬에서 서명 빌드를 시험할 때**: 비밀번호는 반드시 **`~/.gradle/gradle.properties`**(사용자 홈, 레포 밖)에
  둔다. `kotlin/gradle.properties`는 **커밋되는 파일**이라 여기 적으면 비밀번호가 그대로 올라간다.
- **부분 설정은 실패시킨다**: 4종 중 일부만 주면 빌드가 `check`로 멈춘다 — 조용히 debug로 폴백하면
  "서명된 줄 알았는데 업데이트도 업로드도 안 되는" APK가 배포돼 진단이 불가능해진다(#230 리뷰).
  CI도 산출 APK의 인증서를 `apksigner verify`로 확인해 릴리스 노트를 정한다(선언이 아니라 실측).
- **`versionCode`는 `github.run_number`**: 워크플로 파일을 이름 변경·재생성하면 run_number가 1로
  리셋된다. Play는 사용한 versionCode를 영구 기억하므로, 그런 변경 전에는 오프셋(BASE+run_number)을
  도입해야 한다.
- **키 분실 시**: Play App Signing을 쓰면 앱 서명 키는 Google이 보관하므로 **업로드 키만 재발급**하면 된다. Play Console → 설정 → 앱 무결성 → 업로드 키 재설정 요청(새 업로드 인증서 업로드, 반영까지 최대 며칠). 앱 서명 키 자체를 잃는 상황은 Play App Signing 사용 시 발생하지 않는다 — 이것이 자체 서명 대신 Play App Signing을 쓰는 이유.
