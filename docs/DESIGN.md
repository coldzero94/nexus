# NEXUS 디자인 시스템

기준일 2026-07-21. 비주얼 디자인 토큰·정책의 원천. 화면은 여기서 정의한 토큰만 참조한다(하드코딩 색·치수 금지, CLAUDE.md 규칙). 트랙: `docs/BACKLOG.md` E16.

## 1. 컬러 (#251, E16-1)

- **브랜드 스킴 고정이 기본.** `ui/NexusColors.kt`의 정적 라이트/다크 M3 스킴(`NexusLightColors`/`NexusDarkColors`)을 `NexusTheme`이 주입한다.
- **톤**: 따뜻·다정(성장 동료). primary는 캐릭터 앰버(#FFB74D)와 어울리는 warm amber, tertiary는 성장의 sage-green. 두 모드 AA 대비.
- **다이내믹 컬러 정책**: **기본 OFF**(브랜드 고정). 근거 — Android 12+ 다이내믹 컬러를 기본으로 두면 실기기 색이 배경화면 팔레트에 100% 종속돼 앱 고유색이 사라지고, 아이콘·스플래시·데이터 시각화 색과 어긋난다. `NexusTheme(dynamicColor = true)`로 옵트인 가능(갤럭시 One UI 조화 선호 시). 설정 노출은 후속.
- **surfaceContainer 5단계 + surfaceBright/Dim**을 warm 중성 톤으로 채워 Card(surfaceContainerLow)·NavigationBar(surfaceContainer)가 M3 기본 라벤더로 폴백하지 않게 함(#251 리뷰).
- 색은 `NexusColors.kt`에서만 정의. 컴포저블은 `MaterialTheme.colorScheme.*` 토큰만 참조.

## 2. 타이포·모양 (#252, E16-2)

- `NexusTypography`(제목 Bold/SemiBold·본문 한글 넉넉 행간·라벨)와 `NexusShapes`(medium 16dp 등 살짝 둥근 코너)를 `NexusTheme`에 주입. 시스템 폰트(무료, OFL 번들은 후속). 화면은 MaterialTheme.typography/shapes 토큰만 참조.

## 3. 간격·엘리베이션 (#253, E16-3)

- `NexusSpacing`(8pt 스케일 xs4·sm8·md12·lg16·xl24·xxl32 + screen20) + `NexusElevation`. 화면 최상위 패딩을 `NexusSpacing.screen`으로 통일(홈·활동·성장·설정·온보딩), 섹션 간격·카드 패딩을 토큰 참조로 치환. 카드 배경은 톤 엘리베이션(surfaceContainer)이라 그림자는 절제. 값이 아니라 이름(의미)으로 참조.

## 4. 컴포넌트 (#254, E16-4)

- `NexusCard`(헤더 제목+우측 값 슬롯+본문 슬롯, 내부 패딩·간격 토큰) + `CardEmphasis`(Neutral=surface / Highlight=primaryContainer / Celebration=secondaryContainer). HomeCards 6·Growth 히어로(Highlight, #263에서 Level+Affinity 통합)/Stats·Settings 카드·ConnectNotice·StreakRow·홈 다음목표 이관 → 정보 경중이 색으로 읽힘(2단계 위계).
- **`NexusListRow` + `NexusDividedList` (#260, E16-10)**: 목록 행의 단일 규격 — 라벨(SemiBold 앵커) + 보조 + **우측 정렬 값**(bodySmall, 라벨보다 낮은 위계), 라벨 묶음 아래 본문 슬롯. 값 열을 만든 이유는 목록을 훑을 때 "얼마나 했나"를 **세로로 비교**할 수 있어야 하기 때문 — 종류·시간·심박이 한 문장으로 붙어 있으면 그게 안 된다. 라벨·보조·값은 `mergeDescendants`로 **한 노드로 묶는다**(A11Y-TALKBACK §판정: 라벨과 값이 끊겨 들리면 안 된다); 탭 가능한 요소(신뢰 등급 칩 #222)가 오는 본문 슬롯은 묶음 밖에 둬서 동작이 흡수되지 않게 한다. 구분선은 `NexusDividedList`가 **행 사이에만** 넣는다(마지막 뒤는 컨테이너 여백과 겹쳐 두 겹 경계로 보인다).
- **`NexusSwitchCard` (#260)**: 제목+설명+우측 스위치 카드. 설정 탭의 휴식 모드·리마인더가 같은 모양을 각자 조립하고 있었다 — **모양만 통일하고 동작은 호출부에 남긴다**(리마인더는 켤 때 알림 권한 런처를 타므로, 그 분기를 컴포넌트로 들이면 다음 스위치가 또 예외가 된다).
- **탭 세로 리듬 (#260)**: 4탭 모두 `padding(NexusSpacing.screen)` + `Arrangement.spacedBy(NexusSpacing.lg)`. 활동 탭만 수동 `Spacer(28/12/4dp)`를 쓰다가 밀도가 이질적이었다 → 걸음·운동·동기화 3섹션을 `NexusCard`로 구획. **미연결·실패·첫실행 안내는 섹션 카드 안이 아니라 화면 Column의 형제**로 둔다(`ConnectNotice`·`RetryNotice`가 이미 카드라 중첩하면 같은 색 카드 두 겹 + 제목 두 개가 된다 — 홈·성장과 같은 배치). `TabRhythmGuardTest`가 탭 패키지 전체를 훑어 수동 세로 Spacer 0개를 고정한다.
- **`CardEmphasis` 3단계는 서로 다른 색이어야 한다** (#263에서 실제로 깨졌다): 성장 히어로를 `Highlight`(primaryContainer)로 올렸더니, `primaryContainer`를 하드코딩해 쓰던 축하 카드(#61)와 같은 색이 되어 **레벨업 직후 같은 색 카드 두 장이 붙었다** — 축하가 이벤트가 아니라 중복된 헤더로 읽혔다. 축하는 `CardEmphasis.Celebration`(secondaryContainer)으로 옮겼고, `CardEmphasisTest`가 세 단계의 색이 서로 다른지 라이트/다크 모두 고정한다.
- `NexusIcons`는 §6, `VizColors`는 §5.

## 5. 데이터 시각화 (#257~)

- **토큰**: `ui/VizColors.kt` — M3 `ColorScheme` 밖의 차트 전용 팔레트(라이트/다크). `NexusTheme`가
  동일 다크 판정으로 `LocalVizColors` 주입, `VizColors.current`로 접근. 차트 요소는 서피스 대비
  **3:1↑**(비텍스트 UI AA)만 보장 → 값·라벨 텍스트엔 재사용 금지(가독 4.5:1은 `onSurface` 사용).
- **컨디션 게이지 (#257)**: `home/ConditionGaugeBar.kt` — 스톡 프로그레스 대신 Canvas. 바닥(20)~
  MAX(100) 구간을 3존(회복중/안정/좋음) 착색 + 좌측 **바닥 마커**로 "불퇴행" 시각 증거화. 무처벌
  원칙상 회복중도 **적색 금지**(테라코타). 채움 수학·존 분류는 `core/ConditionGauge`(순수·테스트됨).
- **걸음 막대 차트 (#258)**: `steps/StepBarChart.kt` — 활동 탭 텍스트 행을 7일 막대로. 최댓값 y-스케일
  (`core/StepChartScale`, 순수·테스트), 오늘=`walking` 강조·과거 활동일=`walkingMuted`(알파 감쇠는
  라이트에서 3:1 붕괴라 고정 톤)·무활동일=얇은 baseline. 빈 데이터는 '준비 중' 프레이밍(#213 정합).
- **XP 게이지 (#259)**: `ui/XpGauge.kt` — 성장 `TodayXpCard`·홈 `TodaySummaryCard` 공유 컴포넌트.
  오늘 XP를 소프트 니(200)·하드캡(300) 스케일에 그리고, 니를 '벽' 아닌 '천천히 쌓여요' 지점으로
  프레이밍(니 이후 `xpBonusTrack` 로즈틴트 + 마커 + 긍정 캡션). `xp`계열 토큰은 카드 서피스 기준 검증.
  스케일은 `core/XpGaugeScale`(순수·테스트). 이로써 `VizColors`가 완전한 차트 토큰 세트로 자리잡음.
- **접근성**: 색만으로 상태 전달 금지 — 존은 색 점 + 라벨, 오늘 막대는 '오늘' 라벨 병기. 막대별
  `contentDescription`(요일·값)로 단일 포커스 낭독.

### 아이코노그래피 (#265, E16-15)

- `drawable/ic_concept_*.xml` 9종(steps·exercise·condition·level·xp·goal·streak·expedition·energy) —
  탭바와 동일 24dp 그리드·1.9 스트로크 라인 아이콘. `NexusIcons` accessor로 노출, tint는 소비처가
  콘텐츠색 상속(하드코딩 색 금지). `NexusCard(titleIcon=…)` 슬롯으로 제목 앞 개념 아이콘 지원.
- 카피 속 이모지→벡터 치환: 에너지(⚡, ExpeditionCard)·기세(🔥, StreakRow). 나머지 이모지는
  accessor-ready(잔여 치환·위젯은 후속). 장식 아이콘 CD=null(의미는 인접 텍스트).

- **성향 비중 스택 바 (#263, E16-13)**: `growth/AxisShareStackBar.kt` — 스톡 프로그레스 3줄을 하나의 구성 바로. 3줄로는 각 축의 비율만 읽히고 **구성**("나는 걷기 중심인가")은 세 값을 눈으로 합산해야 알 수 있었다.
  - 순서는 `walk → run → strength` **고정**. 비중 순 정렬은 어제/오늘 바를 눈으로 비교할 수 없게 만든다.
  - 비중 정규화·정수 퍼센트는 core(`AxisShareBar`) 순수 로직. 합이 1이 아니면 한 바에 이어 붙였을 때 **덜 차거나 넘쳐** 마지막 세그먼트가 잘린다. 퍼센트는 최대잔여법으로 합 100을 보존하고(절삭하면 33+33+33=99), 0이 아닌 축은 최소 1%를 받는다(그려진 세그먼트가 "0%"로 읽히면 안 된다).
  - 세그먼트는 **직각 맞대기 + 바 전체 클립**. 세그먼트마다 캡슐로 그리면 접점에 바 높이만큼 벌어진 나비넥타이 구멍이 생겨, 2px 구획선 자리에 15dp 얼룩이 남는다. 갭은 dp — raw px은 3배 밀도에서 서브픽셀로 사라진다.
  - **지배 성향은 색이 아니라 높이로 강조한다.** 처음엔 비지배 세그먼트에 알파를 걸었는데, 트랙 위 알파 혼합색이 #263에서 방금 걷어낸 옛 걷기색으로 되돌아가 적록 색약에서 달리기와 다시 붕괴했다 — `walkingMuted`가 고정 톤인 이유와 같은 함정(#258).
  - 색만으로 구분하지 않는다: 범례가 **라벨 + 퍼센트를 항상 병기**하고 0%인 축도 남긴다. 바는 시맨틱을 비운다 — 범례가 값을 갖고 있어 문장을 또 붙이면 이중 낭독이 된다(#258 규칙).
- **카테고리 색 CVD 인접쌍 규칙 (#263)**: 스택 바에서 **맞닿는** 두 색은 색조(Viénot 이색형 시뮬레이션 거리 ≥ 0.10) **또는** 명도(대비 ≥ 1.35:1) 중 하나로 분리돼야 한다. `VizCategoryCvdTest`가 라이트/다크 모두 고정한다. 이 검증이 실제로 팔레트를 고쳤다 — 라이트 `walking`이 두 채널 모두 미달(색조 0.025, 대비 1.01:1)이어서 **색조를 유지하고 명도만 낮췄다**(8A6D2E → 6E5724). 서피스 대비도 3.96→5.59로 함께 올랐고, #258의 오늘/과거 막대 구분도 1.41→1.98:1로 개선됐다.
- **능력치 바 (#263)**: 절대 상한이 없는 값이라 **해금 스탯 중 최댓값 기준 상대 길이** — 바는 "얼마나 찼나"가 아니라 "서로 비교"다. 값이 0인 행은 바를 그리지 않는다(RECOVERY는 S4까지 항상 0이라 영구히 빈 트랙이 남는다). 낭독도 만점을 말하지 않는다 — 최상위는 "가장 높음", 나머지는 "가장 높은 N 대비".

## 6. 브랜드 아이덴티티 (#261, E16-11)

- **제품 마크**: `drawable/nexus_mark.xml` — 자작 기하 심볼(상승 화살표 = 움직임→성장). 캐릭터(#66)와
  구분되는 제품 레벨 마크. 런처 monochrome·워드마크가 공유.
- **런처 아이콘**: `mipmap-anydpi-v26/ic_launcher(_round)` — 어댑티브(앰버 그라디언트 배경 +
  에스프레소 마크 전경) + Android13 monochrome. minSdk 34라 v26 단일 정의. 66dp 세이프존 준수.
- **콜드스타트 스플래시**: `androidx.core-splashscreen` + `Theme.Nexus.Starting`(brand_splash_bg
  앰버 라이트/다크 + 마크 아이콘). `MainActivity.installSplashScreen()`으로 무지 플래시 제거.
- **워드마크**: `ui/NexusWordmark.kt` — 마크 + "NEXUS"(시스템 폰트 Bold·와이드 트래킹, primary).
  온보딩 첫 화면 상단. 라이트/다크 AA.

## 7. 모션 (#262, E16-12)

- **토큰**: `ui/NexusMotion.kt` — Duration 4단(120·240·360·520)·Easing 3종(Emphasized Decelerate/
  Accelerate·Standard)·`CelebrationSpring`. 앱의 단일 '움직임 언어'. 모든 duration은 `motionDuration()`
  경유 → `LocalMotionScale`=0f 주입 시 즉시(리듀스드모션 훅). `scaledDuration`은 순수·테스트됨.
- **모션 스케일 공급**(#217): `NexusTheme`이 `rememberSystemMotionScale()`(= `Settings.Global.ANIMATOR_DURATION_SCALE`,
  포그라운드 복귀마다 재조회)을 `LocalMotionScale`에 주입한다. 시스템에서 애니메이션을 끄면 앱 전역 duration이 0이 되고
  캐릭터 상시 미동(#217)은 정지 프레임으로 남는다. 0.5배 같은 부분 감속도 그대로 반영한다.
- **모션 감축 정책**(#228): 경계 판정은 core `ReduceMotion.isReduced` 하나 — 스케일이 **정확히 0**일 때만
  '제거'다(0.5배는 "느리게"이지 "없애라"가 아니라 연출을 유지). 앱에서는 `reduceMotion()`으로 읽으며,
  **별도 CompositionLocal을 두지 않는다** — 같은 사실의 진실이 둘이면 한쪽만 갱신될 때 화면이 어긋난다.
  적용: ① 캐릭터 프레임 티커 미기동(`CharacterComposer.BaseSprite` 한 곳 → 홈·성장·초기레벨·복귀 씬 전부,
  위젯은 원래 정적) ② 축하 카드는 `celebrationEnter()`로 scaleIn 대신 fadeIn(콘텐츠 동일)
  ③ duration 기반 연출은 `motionDuration()`이 0ms로 만들어 이미 즉시.
- **탭 전환**: `MainActivity` when(tab) → `Crossfade`(Standard, MEDIUM) + `SaveableStateProvider`로
  각 탭 스크롤 위치 보존.
- **게이지 보간**: 컨디션·레벨·오늘XP를 `animatedGaugeProgress`로 감속 보간. 레벨·XP는 `upwardOnly`
  (하락=레벨업 리셋·일일 경계 시 즉시 스냅, 뒤로 안 빠짐=불퇴행), 컨디션은 양방향 완만 감속.
- **축하**: 등장 전환은 `ui/CelebrationEnter.kt`의 `celebrationEnter()` 하나를 공유한다(성장 레벨업·첫 XP).
  기본은 `CelebrationSpring` 스케일+페이드, 모션 감축 시 페이드만 — 위치·크기가 변하지 않는 대체안.

_각 항목은 해당 E16 티켓이 랜딩할 때 이 문서를 같은 PR에서 갱신한다._
