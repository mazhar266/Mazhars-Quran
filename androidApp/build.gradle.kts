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

    buildTypes {
        release {
            // Not minified: the JNI entry points and serializers are looked up by name.
            isMinifyEnabled = false
            // No release key is set up: APKs are signed with the build machine's debug key.
            signingConfig = signingConfigs.getByName("debug")
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
