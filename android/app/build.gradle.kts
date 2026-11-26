import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Local settings come from android/local.properties (never committed):
//   supabase.url=https://<project>.supabase.co
//   supabase.anonKey=<anon public key>
//   release.storeFile=C:/path/to/vendas-release.jks
//   release.storePassword=...
//   release.keyAlias=vendas
//   release.keyPassword=...
// Without the Supabase settings the app runs in demo mode with sample data.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun buildConfigString(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""

val releaseStoreFile: String? = localProperties.getProperty("release.storeFile")

android {
    namespace = "br.vendas.app"

    // Compose 1.13 requires compiling against Android 37.1 or later.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "br.vendas.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.2.5"

        testInstrumentationRunner = "br.vendas.app.HiltTestRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigString(localProperties.getProperty("supabase.url", "")),
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            buildConfigString(localProperties.getProperty("supabase.anonKey", "")),
        )
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                // No release key yet: sign with the debug key so the build can be installed
                // for testing. Create a release key before sharing the app (see README).
                logger.warn("Release build is signed with the DEBUG key: set release.* in local.properties.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
