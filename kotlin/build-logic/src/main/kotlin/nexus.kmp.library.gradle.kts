import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// core 같은 KMP 라이브러리의 공통 타깃 구성.
// iOS 타깃은 앱 없이도 켜둔다 — klib 컴파일이 commonMain의 플랫폼 오염을 CI(리눅스)에서 차단한다 (ARCHITECTURE.md §2).
// Android 타깃(네임스페이스가 모듈마다 다름)은 각 모듈에서 구성한다.
extensions.configure<KotlinMultiplatformExtension> {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
