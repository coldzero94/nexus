plugins {
    // AGP 9.0+는 Kotlin 지원이 내장이라 org.jetbrains.kotlin.android를 적용하지 않는다
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nexus.app"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.nexus.app"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    // Room 스키마 export — 마이그레이션 검증의 기준(#162). schemas/는 커밋 대상.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    testOptions {
        // 단위 테스트에서 android.util.Log 등 안드로이드 API를 no-op으로 (#146)
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.glance.appwidget)
    ksp(libs.room.compiler)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
