// 루트 빌드 파일 — 플러그인을 루트 클래스패스에 한 번만 로드(apply false)해
// 서브프로젝트 간 클래스로더 분리(공유 빌드 서비스 충돌)를 방지한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    validateDistributionUrl = false
}
