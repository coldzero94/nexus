plugins {
    // AGP 9.0+는 Kotlin 지원이 내장이라 org.jetbrains.kotlin.android를 적용하지 않는다
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        // CI가 -Pnexus.versionCode=<run_number>로 주입 (#230) — 로컬 기본 1.
        // Play 내부 트랙은 같은 versionCode 재업로드를 거부하므로 빌드마다 증가해야 한다.
        versionCode = providers.gradleProperty("nexus.versionCode").orNull?.toIntOrNull() ?: 1
        versionName = "0.1.0"

        // TelemetryDeck 앱 ID — gradle.properties(로컬/CI 시크릿)에서 주입, 없으면 빈 값 = 계측 꺼짐 (#46)
        buildConfigField(
            "String",
            "TELEMETRYDECK_APP_ID",
            "\"${providers.gradleProperty("nexus.telemetrydeck.appId").orNull.orEmpty()}\"",
        )
        // Sentry DSN — 없으면 빈 값 = 크래시 수집 꺼짐 (#48, 동일 패턴)
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${providers.gradleProperty("nexus.sentry.dsn").orNull.orEmpty()}\"",
        )
    }

    /**
     * 업로드 서명 (#230) — 키스토어는 **커밋 금지**. CI가 base64 Secret을 파일로 복원하고 경로·비밀번호를
     * gradle property로 넘긴다. 넷 중 하나라도 없으면 signingConfig를 만들지 않아(=null) release가
     * debug 키로 서명된다 — 로컬·포크 PR이 서명 없이도 빌드되게 하는 의도된 폴백.
     *
     * 폴백으로 만든 APK는 **Play 업로드 불가**(업로드 키 불일치)이며, 기기에 이미 깔린 릴리스 빌드를
     * 인플레이스 업데이트하지도 못한다. 절차·키 분실 시 Play App Signing 리셋은 `docs/STACK.md §10`.
     */
    val uploadKeystore =
        providers
            .gradleProperty("nexus.upload.storeFile")
            .orNull
            ?.let { rootProject.file(it) }
            ?.takeIf { it.exists() }
    val uploadStorePassword = providers.gradleProperty("nexus.upload.storePassword").orNull
    val uploadKeyAlias = providers.gradleProperty("nexus.upload.keyAlias").orNull
    val uploadKeyPassword = providers.gradleProperty("nexus.upload.keyPassword").orNull
    val hasUploadKey =
        uploadKeystore != null &&
            !uploadStorePassword.isNullOrBlank() &&
            !uploadKeyAlias.isNullOrBlank() &&
            !uploadKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // 업로드 키가 있으면 그것으로, 없으면 debug 키 폴백(로컬 빌드 무중단)
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
            // 알파 단계에선 축소를 켜지 않는다 — 크래시 스택 가독성 우선(#48 Sentry), R8은 베타에서(#232)
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.telemetrydeck.sdk)
    implementation(libs.sentry.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
