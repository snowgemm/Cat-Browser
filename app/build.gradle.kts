plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

val releaseKeystorePath = providers.environmentVariable("CAT_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("CAT_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("CAT_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("CAT_RELEASE_KEY_PASSWORD").orNull
val releaseVersionCode = providers.environmentVariable("CAT_VERSION_CODE").orNull?.toIntOrNull()
val releaseVersionName = providers.environmentVariable("CAT_VERSION_NAME").orNull
val hasReleaseSigning =
    listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.snowgemm.catbrowser"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "io.github.snowgemm.catbrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 3
        versionName = releaseVersionName ?: "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // GeckoView is a large native engine. Ship only the libraries for the
    // selected device ABI instead of bundling every supported architecture.
    splits {
        abi {
            isEnable = true
            reset()
            include(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
            )
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:160.0.20261104181552")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
