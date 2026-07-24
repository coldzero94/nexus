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

- `NexusCard`(헤더 제목+우측 값 슬롯+본문 슬롯, 내부 패딩·간격 토큰) + `CardEmphasis`(Neutral=surface / Highlight=primaryContainer / Celebration=secondaryContainer). HomeCards 6·Growth Level(Highlight)/Affinity/Stats·Settings 카드·ConnectNotice·StreakRow·홈 다음목표 이관 → 정보 경중이 색으로 읽힘(2단계 위계). NexusListRow·NexusIcons·VizColors는 후속.

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
- **공유 예정**: XP 게이지(#259)가 같은 `VizColors` 참조.
- **접근성**: 색만으로 상태 전달 금지 — 존은 색 점 + 라벨, 오늘 막대는 '오늘' 라벨 병기. 막대별
  `contentDescription`(요일·값)로 단일 포커스 낭독.

_각 항목은 해당 E16 티켓이 랜딩할 때 이 문서를 같은 PR에서 갱신한다._
