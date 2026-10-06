plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/*
 * Release signing (docs/release-signing.md). Secrets never live in this repo: they are read from Gradle properties,
 * normally ~/.gradle/gradle.properties (user home, outside the repo). When any of the four is missing the release
 * build is produced unsigned — `assembleRelease` still works (CI / other machines), it just cannot be installed.
 */
val releaseSigning = listOf(
    "ONEULMUNDEUK_RELEASE_STORE_FILE",
    "ONEULMUNDEUK_RELEASE_STORE_PASSWORD",
    "ONEULMUNDEUK_RELEASE_KEY_ALIAS",
    "ONEULMUNDEUK_RELEASE_KEY_PASSWORD",
).associateWith { providers.gradleProperty(it).orNull }

android {
    // Final identity (2026-10). applicationId can never change after Play upload; namespace = the Kotlin package.
    namespace = "app.oneulmundeuk"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.oneulmundeuk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning.values.all { !it.isNullOrBlank() }) {
            create("release") {
                storeFile = file(releaseSigning.getValue("ONEULMUNDEUK_RELEASE_STORE_FILE")!!.replaceFirst(Regex("^~"), System.getProperty("user.home")))
                storePassword = releaseSigning.getValue("ONEULMUNDEUK_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning.getValue("ONEULMUNDEUK_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("ONEULMUNDEUK_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // app.oneulmundeuk.debug: a separate app for development / fake-AI testing. Real personal records live
            // only in the release-signed app.oneulmundeuk (both can be installed side by side).
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release") // null → unsigned release
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Room schema JSON is committed so future migrations (e.g. embeddings in v2) can be verified.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
