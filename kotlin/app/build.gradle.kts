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
        versionCode = providers
            .gradleProperty("nexus.versionCode")
            .orNull
            // 오타·비정상 값이 조용히 1이 되면 Play 거부·미갱신으로 한참 뒤에야 드러난다 (#230 리뷰)
            ?.let { it.toIntOrNull() ?: error("nexus.versionCode must be an integer, got '$it'") }
            ?: 1
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
    val uploadStoreFileGiven = !providers.gradleProperty("nexus.upload.storeFile").orNull.isNullOrBlank()
    val hasUploadKey =
        uploadKeystore != null &&
            !uploadStorePassword.isNullOrBlank() &&
            !uploadKeyAlias.isNullOrBlank() &&
            !uploadKeyPassword.isNullOrBlank()
    // 키스토어를 지정했는데 나머지가 비면 **조용히 debug로 폴백하지 않는다** — 그 APK는 업데이트도
    // 업로드도 안 되는데 서명된 것처럼 배포돼 진단이 불가능해진다(#230 리뷰). 폴백은 '아무것도 안 준' 경우만.
    check(!uploadStoreFileGiven || hasUploadKey) {
        "업로드 서명 설정이 불완전합니다 — nexus.upload.{storeFile,storePassword,keyAlias,keyPassword} 4종을 " +
            "모두 주거나(서명), 하나도 주지 마세요(debug 폴백). 키스토어 경로 존재 여부도 확인하세요."
    }

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
        debug {
            // 서명이 다른 debug/release가 같은 패키지면 설치가 서로를 막는다 — 별도 패키지로 공존 (#230 리뷰)
            applicationIdSuffix = ".debug"
        }
        release {
            // 업로드 키가 있으면 그것으로, 없으면 debug 키 폴백(로컬 빌드 무중단)
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
            // 알파 단계에선 축소를 켜지 않는다 — 크래시 스택 가독성 우선(#48 Sentry), R8은 베타에서(#232)
            isMinifyEnabled = false
        }
    }

    // Android Lint 게이트 (#242) — 매니페스트 권한·backup_rules 오설정·HealthConnect 회귀를 알파 전에
    // 잡는 무료 검사. 현재 error 0건이라 baseline을 두지 않는다(detekt와 달리 격리할 레거시가 없음) —
    // 새 error가 생기면 그 자리에서 고친다. warning은 보고만 하되 목록은 리포트에서 관리.
    // lintVitalRelease는 Play가 실제로 돌리는 검사라 릴리스 경로에서 함께 돈다(스토어 승격 리스크 선차단).
    lint {
        abortOnError = true
        warningsAsErrors = false
        // 리포트는 CI 아티팩트로 확인
        htmlReport = true
        xmlReport = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room 스키마 export — 마이그레이션 검증의 기준(#162). schemas/는 커밋 대상.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // MigrationTestHelper가 export된 스키마를 읽도록 테스트 자원에 포함 (#233).
    // 이게 없으면 헬퍼가 "Cannot find the schema file" 로 죽는다.
    sourceSets {
        getByName("test") {
            // 스키마 존재 검증(#233)은 클래스패스에서 읽는다.
            resources.srcDir("$projectDir/schemas")
            // MigrationTestHelper는 **앱 assets**에서 찾으므로 debug assets에도 노출한다.
            assets.srcDir("$projectDir/schemas")
        }
    }
    sourceSets {
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    testOptions {
        // 단위 테스트에서 android.util.Log 등 안드로이드 API를 no-op으로 (#146)
        unitTests.isReturnDefaultValues = true
        // Robolectric이 매니페스트·리소스를 읽어야 실 Room/Compose를 JVM에서 띄울 수 있다 (#232)
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.telemetrydeck.sdk)
    // 세션 리플레이는 **의존에서 뺀다** (#352). 이 앱은 화면 캡처를 명시적으로 막아 뒀는데
    // (`CrashReporting`: "화면에 건강 파생 표시값이 있다") 리플레이는 그보다 더 많이 담는다
    // — 연속 프레임이다. 지금은 샘플레이트 기본값이 0이라 안 돌지만, 트리에 있는 한 SDK가
    // 기본값을 바꾸는 순간 조용히 켜진다. NDK(네이티브 크래시)는 남긴다.
    implementation(libs.sentry.android) {
        exclude(group = "io.sentry", module = "sentry-android-replay")
    }
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 호스트측 하네스 (#232) — 에뮬 없이 실 Room·WorkManager·Compose. CI가 ubuntu에서 그대로 돌린다.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
