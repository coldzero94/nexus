# NEXUS 기술 스택 v1

기준일 2026-07-14. 5개 영역(영속성·최소 버전·렌더링·CI/CD·분석)을 2026년 현재 기준으로 웹 검증한 결과를 반영. 최종 확정은 [이슈 #1](https://github.com/coldzero94/nexus/issues/1)에서.

## 결정 요약

| 영역 | 결정 | 한 줄 근거 |
|---|---|---|
| 언어·툴체인 | Swift 6.2+ / Xcode 26.x | 신규 프로젝트 기본 MainActor 격리(Approachable Concurrency)로 동시성 비용 최소 |
| 최소 버전 | **iOS 26** | 현재 iPhone의 79%(출시 시점 85~90% 전망), 워치 사용자는 구조적으로 iOS 26 수렴, 이중 UI(Liquid Glass/구형) QA 제거 |
| UI | SwiftUI 단일 | — |
| 캐릭터 렌더링 | **순수 SwiftUI** (ZStack 레이어 + TimelineView) | 위젯이 SwiftUI 정적 뷰만 지원 → 앱·위젯·워치가 같은 CharacterView 공유 가능한 유일한 선택지 |
| 영속성 | **SwiftData** (가드레일 4종 필수) | 로컬 전용·파생 데이터 위주라 안전 지대. 서드파티 의존 0 |
| CI/CD | **Xcode Cloud** | 멤버십에 월 25 컴퓨트 시간 무료(예상 사용 ~13h), 코드 서명 완전 자동 |
| 분석 | **TelemetryDeck** (무료 50k 이벤트/월) | 프라이버시 우선(IP·기기ID 미수집), 퍼널·리텐션 내장, Apple 5.1.3 정합 |
| 크래시 | Apple 기본 (Organizer + TestFlight) | SDK 하나 줄이는 가치 > MVP 단계 실시간성. 필요 시 Sentry 후행 도입 |
| 테스트 | Swift Testing | NexusCore = 스프레드시트 대조 테이블 테스트 |

## 1. 최소 버전: iOS 26

- 2026-06 Apple 공식 기준 iOS 26+ = 전체 iPhone 79%, 최근 4년 기기 86%. 알파가 나가는 9월엔 iOS 27 공개로 85~90%+ 전망.
- **watchOS 26은 iOS 26 iPhone을 요구** → 핵심 타깃(워치 사용자)은 구조적으로 iOS 26+에 수렴.
- iOS 18 지원 시 얻는 것은 전체의 ~14%(구형 기기·저관여 편중)인데, 대가로 Liquid Glass/구형 UI 이중 QA가 모든 화면·위젯에 발생.
- workoutEffortScore는 iOS 18+ API라 iOS 26 타깃에서 문제없음.
- 리스크: 초기 다운로드 모수 감소 → 사이드 프로젝트는 규모보다 속도 우선이라는 전제와 일치. 출시 직전 Apple 공식 채택률 재확인.

## 2. 영속성: SwiftData — 가드레일 4종이 조건

2025~26 두 번의 WWDC를 거치며 실사용 수준으로 성숙(ModelActor 뷰 갱신 버그 수정, ResultsObserver, 프레디킷 확장). 실패 사례는 대부분 대용량 블롭 저장·CloudKit 동기화에서 발생 — NEXUS는 둘 다 해당 없음(원본의 소스 오브 트루스는 HealthKit, DB엔 파생 데이터만).

**가드레일 (전부 필수):**
1. **첫 릴리즈부터 VersionedSchema + SchemaMigrationPlan** — 버전 없는 스키마로 출시 후 도입하면 기존 사용자 앱이 실행 불가가 되는 크래시가 다수 보고됨. 모델 변경 시 이전 버전 스토어 마이그레이션 테스트를 체크리스트에 포함.
2. **위젯은 DB 직접 접근 금지** — 본앱이 App Group에 위젯용 스냅샷 JSON을 기록하고 위젯은 그것만 읽는다 (TimelineProvider에서 @Query 미동작 + 동시 접근 데드락 보고 회피). 데이터 변경 시 `WidgetCenter.reloadTimelines` 호출.
3. **집계값은 프리컴퓨트** — SwiftData는 배치·집계 쿼리가 약하므로 일/주 롤업 레코드를 따로 저장. 이미지·스프라이트 블롭은 DB 금지(에셋/파일시스템).
4. **리포지토리 프로토콜 뒤에 격리** — 유사시 GRDB/SQLiteData로 교체 가능하게. 무거운 임포트는 ModelActor + 소배치 커밋.

대안 비교: GRDB(성능 최상, SQL·보일러플레이트 비용), SQLiteData(Point-Free — 위젯 제약을 정확히 해소하나 생태계 의존 추가). 이 프로젝트 규모에선 네이티브 무의존이 우선.

## 3. 캐릭터 렌더링: 순수 SwiftUI

- 구조: `ZStack`으로 몸+표정+장비 PNG 레이어 합성, idle은 `TimelineView(.periodic)` 4~8fps 프레임 순환. `scenePhase` background 시 일시정지.
- **선택 이유**: WidgetKit은 SwiftUI 정적 뷰만 지원 → SpriteKit/Rive/Lottie는 위젯용 렌더러를 따로 만들어야 해 이중 구현. 2~4프레임 idle엔 물리·스테이트머신이 불필요. SceneKit이 iOS 26에서 deprecated되며 SpriteKit 장기 전망도 불투명.
- **에셋 파이프라인**: Aseprite(레이어별 PNG, @2x/@3x) → Asset Catalog 단일 소스 → 앱/위젯/워치 공유. 네이밍 규약 `character_{state}_{frame}`, 애니메이션 정의는 JSON 메타데이터로 데이터화.
- **잠금화면·틴트 모드 대응을 에셋 스펙에 처음부터 포함**: 잠금화면 위젯은 항상 모노크롬 → 명도 대비 확보된 실루엣 에셋 별도 제작, `widgetAccentedRenderingMode(.desaturated)` 처리.
- 레벨업 이펙트 등은 SwiftUI Canvas로 우선 구현. 렌더러는 프로토콜로 추상화해 국소적 SpriteKit 도입 여지만 남김.

## 4. HealthKit

- 수집: `HKStatisticsCollectionQuery`(걸음 일별), anchored query(운동 세션 증분), `HKQueryAnchor` 영속화.
- 백그라운드: `HKObserverQuery` + `enableBackgroundDelivery` + 앱 시작 시 소급 동기화. "실시간" 약속은 하지 않는다.
- **workoutEffortScore 스파이크 필요**: 공식 문서 빈약, `HKWorkoutEffortRelationshipQuery` 강제, 워치 워크아웃·수기 입력에만 존재 가능 → S1에서 1~2일 검증하고 부재 시 폴백(시간·빈도만) 공식을 처음부터 설계 (BACKLOG E2-8).

## 5. CI/CD: Xcode Cloud

- 워크플로 A: PR 변경 → Build + Test (시뮬레이터 1개만).
- 워크플로 B: main 머지 → Archive + TestFlight 내부 그룹 자동 배포. `ci_scripts`로 빌드번호 자동화. Xcode 26 컴파일 캐싱 활성화.
- 비용: 멤버십 포함 월 25 컴퓨트 시간 무료 (예상 사용 ~13h/월). GHA는 프라이빗 macOS 10배 차감으로 월 $30~40 예상.
- **비상 절차 문서화**: Xcode Cloud 큐 정지 인시던트 사례가 있으므로 로컬 Organizer 수동 아카이브→TestFlight 업로드 절차를 문서로. 워크플로 구성은 스크린샷으로 레포에 보관(설정이 코드 리뷰 밖에 있는 단점 보완).

## 6. 분석·크래시

- **TelemetryDeck** 무료 티어(월 50k 시그널): 퍼널·리텐션 내장, IP·기기ID 미수집. 얇은 래퍼(프로토콜) 뒤에 두어 이탈 가능하게.
- **이벤트 allowlist를 코드로 강제**: 파라미터에 건강 원천·파생 수치(걸음 수, 운동 시간, 심박, XP 근거값) 일절 금지 — 상호작용 사실만 전송(`level_up`은 보내되 근거 걸음 수는 미전송). Apple 5.1.3 위반으로 앱 제거된 사례 있음. PR 체크리스트에 이벤트 스키마 리뷰 항목.
- 크래시: MVP는 Xcode Organizer + TestFlight 크래시 피드백(+선택적 MetricKit). 정식 출시 후 공백이 체감되면 Sentry 무료 후행 도입(브레드크럼에 건강 데이터 금지 전제).

## 7. 프로젝트 구조

```
nexus/
├── Nexus.xcworkspace
├── App/                      # 앱 타깃 (조립만, 로직 없음)
├── Widgets/                  # WidgetKit 익스텐션 (스냅샷 JSON만 읽음)
├── Packages/
│   ├── NexusCore/            # XP 엔진·클래스·상태머신 — 순수 Swift, UI/DB 의존 0
│   ├── NexusData/            # SwiftData 모델·리포지토리·롤업 (VersionedSchema)
│   ├── NexusHealth/          # HealthKit 어댑터 (수집·중복 병합·신뢰 필터)
│   └── NexusUI/              # 디자인 토큰, CharacterView, 공용 컴포넌트
├── Assets/                   # Aseprite 원본 + 내보내기 스크립트
└── docs/
```

의존 방향: `App → NexusUI/NexusData/NexusHealth → NexusCore`. NexusCore는 아무것도 모른다(스프레드시트 대조 테스트가 가능한 이유).

## 8. 컨벤션 요점

- 동시성: Swift 6.2 기본 MainActor 격리, 백그라운드 작업만 명시적 격리.
- 상태: `@Observable` (Observation framework), 화면 단위 뷰모델.
- 테스트: Swift Testing. NexusCore는 케이스 테이블(입력→기대 XP) 기반 — 치완 스프레드시트와 같은 표를 공유.
- 지역화: String Catalog, ko 우선.
- 연 1회(가을) 툴체인 마이그레이션 버퍼 1~2주를 로드맵에 배정.
