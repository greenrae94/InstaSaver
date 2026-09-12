// AGP 9.x provides Kotlin support natively; the separate kotlin-android plugin
// refuses to apply on top of it (see https://kotl.in/gradle/agp-built-in-kotlin)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.raegreen.instasaver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.raegreen.instasaver"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing details come from ~/.gradle/gradle.properties, never from this repo, so the
    // keystore and its password cannot be committed. Without them a release build is simply
    // unsigned — debug builds are unaffected.
    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("INSTASAVER_STORE_FILE").orNull

            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("INSTASAVER_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("INSTASAVER_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("INSTASAVER_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Kotlin 2.x sets the JVM target in its own top-level block, not inside android { }
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.work.runtime)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
