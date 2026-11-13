import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "fi.mazhar.quran.ai"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "fi.mazhar.quran.ai"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // CI passes -PappVersionName (from the release tag) and -PappVersionCode (the run number).
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: libs.versions.app.get()
    }

    // Release signing comes from the environment (see .github/workflows/release.yml and the README).
    // Without it, release builds are signed with the debug key so that the build still works.
    val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Not minified: the JNI entry points and serializers are looked up by name.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":llm-android"))
    implementation(project(":stt-android"))
    implementation(libs.androidx.activity.compose)
}
