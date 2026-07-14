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
| 배포 | 일상: **Firebase App Distribution** / 주 1회: **Play 내부 트랙** | FAD는 무심사·즉시, 내부 트랙은 워치 앱 배포 경로 확보용 |
| 분석 | TelemetryDeck Kotlin SDK 7.x (무료 50k/월) | 리텐션·세션 빌트인, 식별자 해시. 대안 Aptabase |
| 크래시 | Sentry 무료(5k/월, tracing off, PII off) + Play vitals 보조 | vitals 단독은 초기 소규모에서 임계치 미달로 안 보임. **Crashlytics 배제**(동의 전 자동 수집) |
| 프로젝트 구성 | AGP 9.x + Gradle 9.6.x + JDK 17 + 버전 카탈로그 + build-logic 컨벤션 플러그인 | 2026-05 JetBrains 신 KMP 표준 구조 — [ARCHITECTURE.md](./ARCHITECTURE.md) |
| **서버 (S9~)** | Supabase-in-repo (`supabase/`: 마이그레이션·Edge Functions=Deno) | 공식 CLI 표준 구조, CI로 db push·functions deploy. 무료 티어 + keep-alive cron |
| **웹** | Astro + Cloudflare Workers 무료 — 랜딩·폴리시(정적) + 공유 스냅샷(SSR·OG) | KMP 웹 타깃 기각(CMP Web=Beta·SEO 불가). Vercel Hobby는 비상업 한정이라 기각 |
| **공유 전략** | **core = KMP 모듈**(`com.android.kotlin.multiplatform.library` 플러그인) — **iOS 타깃은 지금부터 켜되 iosApp은 게이트 후** | klib 컴파일은 Linux CI에서 가능 → macOS 러너 없이 commonMain의 Android 오염을 매 PR 차단. 구 플러그인 방식은 AGP 10에서 제거 예정 |

## 1. Health Connect 파이프라인 (검증 완료)

- **걸음**: `aggregate(StepsRecord.COUNT_TOTAL)` — readRecords로 직접 읽으면 이중 카운트. **운동 세션**: `ExerciseSessionRecord` (RUNNING/WALKING/STRENGTH_TRAINING/WEIGHTLIFTING/CALISTHENICS 등 타입 상수로 클래스 매핑 직접 가능), 세션 시간 범위로 HeartRateRecord 연계 조회.
- **수기 입력 필터**: `metadata.recordingMethod == RECORDING_METHOD_MANUAL_ENTRY` → XP 제외. SDK 1.1.0부터 기록 시 recordingMethod 의무화라 신규 데이터 신뢰도 높음. 단 서버측 필터는 dataOrigin만 — recordingMethod는 읽은 뒤 앱에서 거른다. 과거 데이터는 UNKNOWN 많음.
- **소스 신뢰 등급**: `dataOrigin`(패키지명, 위조 불가)으로 Tier 부여 — 삼성헬스/온디바이스/자사 = 신뢰, 미상 서드파티 = 감점. ⚠️ **2026-06부터 온디바이스 걸음의 dataOrigin이 "android"에서 기기별 SPN으로 변경** — `getCurrentDeviceDataSource()`로 SPN을 조회해 둘 다 필터에 포함. 화이트리스트는 하드코딩 금지(원격 구성 가능하게). 삼성헬스 패키지명은 실기기 실측으로 확정.
- **동기화**: 15분 주기 WorkManager + **Changes API 증분**(UpsertionChange/DeletionChange — 삭제가 보상 이벤트 트리거) + 앱 실행 시 즉시. 토큰 30일 만료 → 전체 재읽기 폴백 필수. 레이트 리밋(수치 비공개, 백그라운드 더 엄격) 때문에 폴링 남용 금지.
- **권한 3종**: 읽기 + `READ_HEALTH_DATA_IN_BACKGROUND`(위젯 자동 갱신) + `READ_HEALTH_DATA_HISTORY`(30일 초과 과거 → 온보딩 초기 레벨 부여). 셋 다 처음부터 선언 — 나중에 추가하면 재심사.
- **강도 지표**: HealthKit workoutEffortScore의 대응물 없음 → 세션 심박 시계열로 심박존 체류를 자체 산출(추정치임을 UI 표기).
- **삼성헬스 경로의 현실**: 워치→폰 삼성헬스→HC는 **30~60분 지연(추정) + 버전에 따른 동기화 버그 이력**(2026-07에도 운동 누락 보고). "운동 끝나자마자 반영"은 보장 불가 → 원정 연출("모험에서 돌아오는 중")로 UX 흡수. 사용자가 삼성헬스에서 HC 쓰기 권한을 켜야 함 — 온보딩에서 안내 필수. **S0에서 치완 실기기로 도달 시간·dataOrigin·recordingMethod 실측이 최우선 스파이크.**

## 2. Google Play 정책 — 타임라인 크리티컬 ⚠️

- **Play Console 개인 계정($25 일회성)을 지금 생성** — 신규 개인 계정은 프로덕션 출시 전 **"비공개 테스트에 12명이 14일 연속 옵트인"** 게이트가 있다(2026-07 현재 유효). 테스터 이탈 대비 **15명+ 풀**을 미리 확보. 내부 테스트 트랙은 이 요건과 무관하게 즉시 사용 가능.
- **Health apps declaration**: HC 데이터 타입별 선언·심사(≤7일, 거절 루프 사례 다수)가 **closed test 트랙에도 적용**. 거절 사유 1순위 = 과다 권한. 최소셋(Steps, ExerciseSession, Distance, TotalCaloriesBurned, HeartRate)만, 타입마다 "XP/능력치 변환에 필요" 정당화 — **'health-integrated games'가 공식 승인 유스케이스**라 게임화 목적을 숨기지 말고 명시. 카테고리는 'Activity and fitness'(Medical 아님), 스토어 문구에 질병·치료 표현 배제 + '의료기기 아님' 문구.
- **광고 전면 금지**(건강 데이터 보유 앱) → IAP 확정(이미 방침). **분석 이벤트에 건강 파생 수치(XP·레벨 수치 포함) 탑재 금지** — "SDK가 의도치 않게 수신해도 위반" 조항.
- **Data safety**: '수집' = 기기 밖 전송 기준 → 서버 없는 로컬 앱은 유리. 프라이버시 폴리시 URL 필수(스토어·HC 권한 화면 일치).
- **타임라인**: 개발 완료 → 공개 출시까지 최소 4주, HC 심사 거절 1회 가정 시 6주. 알파(FAD/내부 트랙)는 이 게이트와 무관하게 즉시 가능.

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

## 5. CI/CD·배포

- GitHub Actions: PR → lint+단위 테스트 / main 머지 → 서명 AAB → **Firebase App Distribution** / 태그 → Play 내부 트랙(`r0adkll/upload-google-play`). gradle/actions v6 + configuration cache로 ~5분.
- Play App Signing(업로드 키 분실해도 리셋 가능 — 2인 팀에 결정적), 키스토어는 GitHub Secrets(base64), 커밋 금지.
- 계측(에뮬레이터) 테스트는 CI에서 제외(분수 폭증) — 로컬/야간.
- 워치 앱은 FAD로 설치 안 됨(추정) → Play 내부 트랙 또는 adb 사이드로드.

## 6. 개발 환경 (맥 + 에뮬레이터)

- API 34/36 에뮬레이터(HC 내장) + **Health Connect Toolbox**(공식 컴패니언)로 걸음·운동 세션 합성 데이터 주입. 디버그 빌드에 시드 데이터 버튼. 단위 테스트는 FakeHealthConnectClient.
- 치완 갤럭시 실기기 전담 검증: 삼성헬스→HC 동기화 지연 분포, dataOrigin 실측, One UI 배터리 정책, (보유 시) 갤럭시워치 경로.

## 7. 프로젝트 구조

**모노레포 전체 구성은 [ARCHITECTURE.md](./ARCHITECTURE.md)가 단일 기준** — `android/`(Gradle 루트: build-logic + core KMP + app) · `web/`(Astro) · `supabase/`(Deno). `android/` 내부 논리 모듈: core(XP 엔진·상태머신 — commonMain), data(Room·원장), health(HC 어댑터), ui(토큰·캐릭터 컴포저), app(조립·Glance).

의존 방향: `app → ui/data/health → core`. core는 치완 스프레드시트와 같은 케이스 테이블로 테스트하며, iOS 확장 시 무수정 재사용. 건강 어댑터는 인터페이스만 common에 두고 플랫폼별 구현(Health Connect/HealthKit). UI·위젯·워치·웹은 공유하지 않는다(리텐션 표면이 전부 플랫폼 전용 — Compose Multiplatform은 iOS·웹 모두 비채택, [ARCHITECTURE.md §3](./ARCHITECTURE.md)).

## 8. 비용 원칙 — 전부 무료 티어

**총 필수 지출 = Play Console $25 일회성. 그 외 전부 무료.**

| 항목 | 무료 범위 | 초과 시 대응 (트리거 명시) |
|---|---|---|
| GitHub Actions | 프라이빗 월 2,000분 (리눅스 1x) | 초과 상시화 → **리포 퍼블릭화 우선**(무제한 무료), 그다음이 과금($0.006/분) |
| Firebase App Distribution | 무제한 무료 | — |
| Play 내부/비공개 트랙 | 무료 (계정비 $25 일회성뿐) | — |
| TelemetryDeck | 월 50k 이벤트 | 초과 → 이벤트 다이어트 먼저, 그래도 부족하면 Aptabase 셀프호스트(무료)로 이전 (래퍼 뒤라 교체 쉬움) |
| Sentry | 월 5k 에러 | rate limit 설정으로 크래시 루프 방어. 초과 상시화면 vitals만으로 회귀 |
| 에뮬레이터·HC Toolbox | 무료 | — |
| 아트 | **무료 에셋·AI 생성 우선** | 구매는 최후 수단, 결정은 이슈 #1에서 |
| 2단계 서버 (게이트 후) | Supabase Free(50k MAU·500MB)로 PoC·초기 | 실사용자 증가 시에만 Pro $25/월 |
| iOS 확장 (게이트 후) | — | Apple Developer $99/년은 그 시점에 발생 |

원칙: 유료 전환은 "무료 한도 초과가 2개월 연속"일 때만 검토하고, 그 전에 무료 대안(퍼블릭화·셀프호스트·다이어트)을 먼저 소진한다.

## 9. 컨벤션 요점

- 상태: Compose + ViewModel(+ Flow). 테스트: JUnit5 + core는 케이스 테이블 기반.
- 문자열: 리소스 externalize, ko 기본.
- 분석 이벤트 allowlist를 코드로 강제 — 건강 원천·파생 수치 필드 금지(§2 정책).
- 연 1회(가을) AGP/Gradle 마이그레이션 버퍼.
