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

## 3. 간격·엘리베이션 (#253, E16-3 예정)

- 8pt 기반 Spacing 토큰(xs4·sm8·md12·lg16·xl24·xxl32) + Elevation 토큰 예정. 현재 dp 리터럴 산발.

## 4. 컴포넌트 (#254~, E16-4 예정)

- `NexusCard`(emphasis 위계)·`NexusListRow`·`NexusIcons`·`VizColors`(데이터 시각화) 예정.

## 5. 데이터 시각화 (#257~, 예정)

- 컨디션 게이지(바닥20 마커·무처벌)·걸음 막대·XP 게이지. `VizColors` 토큰 공유. 색만으로 상태 전달 금지(라벨 병기).

_각 항목은 해당 E16 티켓이 랜딩할 때 이 문서를 같은 PR에서 갱신한다._
