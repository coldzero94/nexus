# NEXUS 모노레포 아키텍처

기준일 2026-07-15. 5개 영역(KMP 구조·웹-KMP·폴리글랏 툴링·Supabase-in-repo·공유 웹) 웹 검증 결과. **2026년 5월 JetBrains가 KMP 기본 구조를 공식 변경**(shared=순수 라이브러리, 앱 진입점 분리 — AGP 9가 강제)했고, 이 문서는 그 신 표준을 따른다.

## 0. 한 장 요약

```
nexus/  (모노레포 — 폴리글랏 3파트, 모노레포 툴 없음)
├── android/                    # 독립 Gradle 루트 · Kotlin 2.4.x · AGP 9.x
│   ├── build-logic/            #   컨벤션 플러그인 (nexus.kmp.library 등, includeBuild)
│   ├── gradle/libs.versions.toml
│   ├── core/                   #   KMP 라이브러리 — XP 엔진·상태머신·모델 (commonMain)
│   │                           #   · com.android.kotlin.multiplatform.library 플러그인
│   │                           #   · iosArm64 타깃 지금부터 활성 (아래 §2 참고)
│   ├── app/                    #   Android 앱 진입점 — Compose·Health Connect·Room·Glance 조립
│   └── (widget는 app 내 모듈)
├── iosApp/                     # 게이트 후 생성 — 지금은 디렉터리도 만들지 않음
├── supabase/                   # (S9~) config.toml · migrations/ · functions/(Deno) · seed.sql
├── web/                        # Astro — 랜딩·프라이버시(정적) + 공유 스냅샷(SSR·OG)
│   └── public/.well-known/assetlinks.json
├── docs/                       # 기획·설계 (진실의 원천)
└── .github/workflows/          # android.yml · web.yml · supabase.yml + ci-ok 게이트
```

- 각 파트는 자기 도구를 유지: `android/`=Gradle, `web/`=pnpm(Node), `supabase/`=Deno(supabase CLI). **pnpm workspace는 web만 포함, supabase/는 Deno 경계 안에 격리**(Node 도구 적용 시 해석기 충돌).
- Nx·Turborepo·Bazel **도입 안 함** — Turborepo는 Gradle 태스크를 캐싱 못 하고, 2인·3파트 규모에선 Gradle 캐시 + CI paths 게이트로 충분. 재검토 트리거: 웹 패키지 5개+ 상호 의존, 언어 경계 코드젠, CI 10분+.

## 1. Android/KMP (2026-05 신 표준 반영)

- **구조 원칙**: core는 순수 KMP 라이브러리, `app/`이 앱 진입점 — AGP 9.0이 이 분리를 요구. 참고 레퍼런스: [Confetti](https://github.com/joreilly/Confetti)(앱들+shared+backend+build-logic 동형 구조), Kotlin/KMP-App-Template.
- **버전 (2026-07 안정판)**: Kotlin **2.4.0**(2026-06 — 2.3.x 아님), AGP 9.x + Gradle 9.6.x. core의 Android 타깃은 **`com.android.kotlin.multiplatform.library`** 플러그인 — 구방식(`com.android.library`+androidTarget)은 AGP 10에서 제거 예정이라 처음부터 신 플러그인.
- **build-logic 컨벤션 플러그인**: 모듈 유형별 설정(`nexus.kmp.library`, `nexus.android.app`)을 includeBuild로 — 설정 중복 제거, buildSrc와 달리 변경 시 전체 재빌드 없음.
- **Amper 채택 안 함**: 2026-05 저장소 아카이브 → kotlin-toolchain 0.x로 이관, 여전히 실험 단계.

## 2. iOS 대비 규율 — "타깃은 켜고, 앱은 안 만든다"

핵심 발견: iOS 타깃이 없으면 **컴파일러가 commonMain의 Android 오염(java.time 등)을 못 잡는다** — 나중에 iOS를 켤 때 이게 최대 숨은 비용. 대응:

- core에 `iosArm64()`/`iosSimulatorArm64()` 타깃을 **지금부터 활성**. klib 컴파일은 cinterop 의존성이 없는 한 **Linux CI에서 가능**(Kotlin/Native 크로스 컴파일) → macOS 러너(리눅스의 10배 과금) 없이 commonMain 순수성을 매 PR 검증.
- iosApp·XCFramework export·Swift export(2.4.0에서 Alpha)는 게이트 후.
- Room(2.8.x)·DataStore(Preferences만)·Lifecycle은 KMP 지원이라 core/data 설계 유효. Health Connect·Glance는 Android 전용 → `app/`에 격리.

## 3. 웹 — 순수 TS(Astro), KMP 웹 타깃 불필요 (판정)

용도가 ① 랜딩·프라이버시 폴리시(정적) ② 크루 초대·모임·캐릭터 공유 스냅샷(읽기 전용)이므로:

- **Compose Multiplatform for Web 기각**: 2026-07 현재 Beta + 캔버스 렌더링이라 **OG 미리보기·SEO가 구조적으로 불가능** — 공유 링크의 존재 이유(카톡 카드)와 정면 충돌. Kotlin/JS export도 TS 정의 생성이 실험 단계라 과잉.
- **웹에는 게임 로직이 없다**: 표시할 파생값(XP·레벨)은 앱이 기록한 서버 스냅샷을 그대로 읽음. **파생값의 단일 진실 공급원은 서버/스냅샷** — 웹에서 XP를 TS로 재구현하지 않는다(드리프트 방지 원칙). 클라이언트 실행이 정말 필요해지면 core에 js 타깃 추가가 공식 경로.
- **스택**: Astro + `@astrojs/cloudflare` 어댑터. 랜딩·폴리시는 완전 정적, 공유 페이지(`/s/crew/{publicId}`)는 SSR로 Supabase를 읽어 **OG 태그를 초기 HTML에 포함**(카카오 스크래퍼는 JS를 실행하지 않음). CI에 OG 스모크 테스트(curl 후 og:title 확인).
- **호스팅: Cloudflare Workers(static assets) 무료** — 정적 무제한·상업 사용 허용·커스텀 도메인 SSL. Vercel Hobby는 비상업 한정(약관 리스크), GitHub Pages는 상업 금지+SSR 불가라 기각.

## 4. 딥링크 — 자기 도메인 App Links

- 공유 URL 자체를 App Links 도메인으로: 설치자는 OS가 앱을 열고, 미설치자는 같은 URL이 웹 스냅샷(읽기 전용 + 설치 CTA 배너 — 강제 리다이렉트 금지, 당근 패턴).
- `assetlinks.json`은 `web/public/.well-known/`에 커밋. **3대 함정**: ① SHA256은 로컬 키가 아니라 **Play App Signing의 앱 서명 키**(+디버그 키 배열) ② `.well-known` 경로에 리다이렉트 금지 ③ robots.txt가 `.well-known`을 막으면 무증상 실패.
- **도메인은 알파 전 확정** — Android 14 이하는 앱 설치/업데이트 시에만 App Links를 검증해 도메인 변경 비용이 큼 (BACKLOG E8-10).

## 5. Supabase-in-repo (S9~)

- 루트 `supabase/` 단일 디렉토리(공식 표준): config.toml·migrations/·functions/(공유 코드는 `functions/_shared/`)·seed.sql. 로컬 루프 = `supabase start`(Docker) → `db reset` → `functions serve`.
- **마이그레이션 규율**: 모든 스키마 변경은 `supabase migration new`로만, 대시보드 직접 수정 금지(드리프트 시 CI가 깨짐). PR CI = 로컬 스택에서 `db reset` 검증 + `gen types typescript --local` 후 `git diff --exit-code`(타입 드리프트 검출). main 머지 = `db push` + `functions deploy --use-api`.
- **Kotlin 타입은 공식 생성기 없음**(TS/Go/Swift/Python만) → core에 수동 `@Serializable` 모델 + **TS 타입 diff를 "Kotlin 모델도 갱신하라"는 리뷰 신호로** 사용.
- **무료 티어 운영 함정**: ① 7일 무활동 시 프로젝트 자동 정지, 정지 90일 후 복원 불가 → **주 2회 keep-alive cron**(E10-8) ② 활성 무료 프로젝트 2개 한도가 계정 합산 → 스테이징 없이 "로컬=프리뷰, 원격 1개=프로덕션(서울)" ③ Branching 2.0은 Pro 전용이라 설계에서 제외. 실사용자가 붙으면 Pro $25/월이 정지 리스크 제거를 겸하는 가장 싼 보험.

## 6. CI — paths 게이트 + 단일 required check

- 워크플로 3개(android/web/supabase)를 항상 트리거하되, 첫 job에서 `dorny/paths-filter`(**SHA 핀 고정** — tj-actions 오염 사고 CVE-2025-30066 교훈)로 변경 파트를 감지해 후속 job을 `if`로 게이트.
- **함정 회피**: 워크플로 수준 `on.paths` + required check 조합은 스킵 시 Pending으로 머지가 영구 차단됨 → `if: always()`로 취합하는 **단일 `ci-ok` job만 required check**로 지정.
- android CI에 core의 iOS klib 컴파일 포함(§2). 계측 테스트는 CI 제외(로컬/야간).

## 7. 무료 원칙 정합 (STACK.md §8 연장)

| 파트 | 비용 |
|---|---|
| 웹 호스팅 | Cloudflare Workers 무료 (정적 무제한) |
| 도메인 | 유일한 신규 지출 후보 (~₩15,000/년) — 알파 전 필수 |
| Supabase | Free 티어 + keep-alive cron. 실사용자 시점에 Pro $25/월 |
| CI | 리눅스 러너만 (macOS 러너는 iosApp 만들 때까지 0분) |
