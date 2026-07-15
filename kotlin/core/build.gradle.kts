plugins {
    id("nexus.kmp.library")
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.nexus.core"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }
}

kotlin {
    sourceSets.commonMain.dependencies {
        // 게임 데이터 테이블(애니메이션 메타 E4-1, 기분 규칙 E4-4, 대사 풀 E4-5)의 공통 파서
        implementation(libs.kotlinx.serialization.json)
    }
}
