plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lilyan_lefevre.milauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lilyan_lefevre.milauncher"
        minSdk = 26 // java.time
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        // GitHub repository ("owner/name") whose latest release the in-app updater checks.
        val updateRepo = providers.gradleProperty("milauncher.updateRepo").getOrElse("LilyanLefevre/MiLauncher")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // Release key from the environment (CI secrets), see README. Without it, fall back to the debug key.
    val releaseKeystore = System.getenv("SIGNING_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json is only a stub in JVM tests: ship the real implementation.
    testImplementation(libs.json)
}
