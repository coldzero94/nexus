# 개발 환경 세팅 가이드

Android 개발을 시작하기 위한 셋업. **윈도우(치완) 기준으로 먼저 쓰고, 맥(찬영)은 아래 별도 섹션.** 20~30분이면 끝난다.

## 1. 공통 — 설치할 것은 사실상 2개

| 순서 | 무엇 | 어디서 | 비고 |
|---|---|---|---|
| 1 | **Git** | [git-scm.com](https://git-scm.com) (윈도우) / 맥은 기본 내장 | 설치 옵션은 전부 기본값으로. GUI가 편하면 [GitHub Desktop](https://desktop.github.com)도 좋음 |
| 2 | **Android Studio** | [developer.android.com/studio](https://developer.android.com/studio) | JDK 21이 내장돼 있어서 **자바 별도 설치 불필요**. 설치 마법사에서 Android SDK도 같이 설치됨 |

## 2. 프로젝트 받아서 열기

```
git clone https://github.com/coldzero94/nexus.git
```

Android Studio에서 **Open → `nexus/kotlin` 폴더를 연다** (레포 루트가 아니라 `kotlin/`이 Gradle 루트다 — 문서·웹·서버가 같은 레포에 살기 때문 → [ARCHITECTURE.md](./ARCHITECTURE.md)).

- 처음 열면 Gradle sync가 몇 분 돈다 (의존성 다운로드).
- `kotlin/local.properties`(SDK 경로)는 Android Studio가 자동 생성한다. 이 파일은 커밋 금지(.gitignore에 있음).
- 줄바꿈(CRLF) 설정은 신경 쓸 필요 없음 — `.gitattributes`가 강제한다.

## 3. 실행해보기 — 3가지 방법

### A. 에뮬레이터 (가상 폰)
Android Studio 우측 상단 Device Manager → **Create Device** → Pixel 7 + API 36 이미지 → ▶ 실행 → 상단 Run 버튼(▶)으로 `app` 실행 → **"Hello NEXUS"가 뜨면 성공.**

### B. 갤럭시 실기기 (치완의 주 무기)
1. 폰 설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드번호 7번 연타** (개발자 모드 켜짐)
2. 설정 → 개발자 옵션 → **USB 디버깅** 켜기
3. USB 연결 → 폰에 뜨는 "디버깅 허용" 수락 → Android Studio 기기 목록에 폰이 보임 → Run
- 케이블 없이 하려면: 개발자 옵션 → 무선 디버깅 (같은 와이파이)

### C. 빌드된 APK 설치 (개발 안 할 때)
main에 머지될 때마다 [Releases](https://github.com/coldzero94/nexus/releases)에 APK가 자동 업로드된다. 폰에서 다운로드 → 설치 (출처 불명 앱 허용 1회 필요).

## 4. 커맨드라인 (선택 — Studio가 다 해주지만)

`kotlin/` 폴더에서:

| 명령 (윈도우는 `gradlew.bat`, 맥은 `./gradlew`) | 무엇 |
|---|---|
| `gradlew :core:jvmTest` | 게임 로직 테스트 (케이스 테이블) — core 건드리면 커밋 전 필수 |
| `gradlew :app:assembleDebug` | APK 빌드 → `app/build/outputs/apk/debug/` |

iOS 관련 태스크(`:core:compileKotlinIosArm64`)는 윈도우에서 돌리지 않는다 — **CI가 매 PR마다 대신 검증**한다.

## 5. Health Connect 테스트 데이터 (S1부터 필요)

에뮬레이터에는 삼성헬스가 없으므로 **[Health Connect Toolbox](https://goo.gle/health-connect-toolbox)**(구글 공식)를 설치해 가짜 걸음·운동 세션을 주입한다. 실기기에서는 실제 삼성헬스 데이터가 흐른다 — 삼성헬스 → 설정 → Health Connect 연동을 켜야 함(이슈 #12 실측 항목).

## 6. 맥 (찬영 환경 메모)

- JDK: `brew install openjdk@21` → `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`
- SDK: `brew install --cask android-commandlinetools` → `local.properties`에 `sdk.dir=/opt/homebrew/share/android-commandlinetools`
- 이 맥에서는 `./gradlew`의 배포판 다운로드가 로컬 TLS 문제로 막혀 있어 `brew install gradle`(동일 9.6.1)로 실행 — CI·다른 머신에서는 wrapper 정상 (CLAUDE.md 참고)

## 7. 트러블슈팅

| 증상 | 해결 |
|---|---|
| Gradle sync 실패: JDK 관련 에러 | Studio 설정 → Build Tools → Gradle → Gradle JDK를 **Embedded JDK (JBR 21)**로 |
| 에뮬레이터가 안 만들어짐/느림 (윈도우) | BIOS에서 가상화(VT-x/SVM) 켜기 — 요즘 PC는 대부분 기본 on |
| `adb devices`에 폰이 안 보임 | USB 케이블 교체(충전 전용 케이블 함정) → 폰의 디버깅 허용 팝업 확인 |
| 빌드는 되는데 앱이 구버전 | Run 전에 상단 기기 선택이 맞는지 확인, 안 되면 `gradlew :app:installDebug` |

## 8. 다음 읽을 것

- 협업 방식·이슈·PR 규칙: [WORKFLOW.md](./WORKFLOW.md)
- 지금 스프린트에서 뭘 하는지: [SPRINTS.md](./SPRINTS.md)
- 코드 규칙(Claude Code용이지만 사람에게도 유효): [/CLAUDE.md](../CLAUDE.md)
