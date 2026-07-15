// 루트 빌드 파일 — 플러그인을 루트 클래스패스에 한 번만 로드(apply false)해
// 서브프로젝트 간 클래스로더 분리(공유 빌드 서비스 충돌)를 방지한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// ── 코드 품질 (E1-7): 로컬 spotlessApply 자동 수정 / CI spotlessCheck·detekt 차단 ──
spotless {
    kotlin {
        target("*/src/**/*.kt", "build-logic/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
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
