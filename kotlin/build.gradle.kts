// 루트 빌드 파일 — 플러그인을 루트 클래스패스에 한 번만 로드해 서브프로젝트 간
// 클래스로더 분리(공유 빌드 서비스 충돌)를 방지한다. 빌드용 4개는 apply false,
// 품질 플러그인 2개(spotless·detekt)는 루트에 직접 적용.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// ── 코드 품질 (E1-7): 로컬 spotlessApply 자동 수정 / CI spotlessCheck·detekt 차단 ──
spotless {
    kotlin {
        // build-logic의 .kt는 없음 — 스크립트 플러그인(.gradle.kts)은 아래 kotlinGradle이 담당
        target("*/src/**/*.kt")
        targetExclude("**/build/**")
        // 루트 .editorconfig의 Composable 예외가 이 환경의 Spotless ktlint에 적용되지 않음을
        // 실측 확인(#131) → override로 고정. 제거하면 회귀함. .editorconfig 쪽과 함께 수정할 것.
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
        )
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "build-logic/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    source.setFrom("core/src", "app/src", "build-logic/src")
    config.setFrom("detekt.yml")
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
    parallel = true
}

dependencies {
    detektPlugins(libs.detekt.compose.rules)
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    validateDistributionUrl = false
}

// 의존성 락 (#244) — 런타임 클래스패스만 잠근다. 전 configuration을 잠그면 Gradle·AGP가 끌어오는
// 빌드 도구(netty·bouncycastle 등)까지 락파일에 들어가, 앱에 출하되지도 않는 CVE가 스캔 결과를
// 뒤덮는다(#244 실측: 전체 락 시 다수 → 런타임만 락 시 2건). 락파일은 OSV-Scanner의 입력이다.
// 락은 버전을 strictly로 고정한다 — 카탈로그에서 버전을 바꿔도 락파일 값이 이긴다(실측).
// 그래서 의존성을 올린 뒤에는 반드시 `./gradlew :app:dependencies --write-locks`로 갱신해야
// 실제로 반영된다. Dependabot PR도 이 갱신을 포함해야 한다.
subprojects {
    configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
        resolutionStrategy.activateDependencyLocking()
    }
}
