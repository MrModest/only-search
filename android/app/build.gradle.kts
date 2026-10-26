plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.onlysearch"
    compileSdk = 37
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "app.onlysearch"
        minSdk = 28 // SoulverCore's Android build targets API 28
        targetSdk = 35
        // CI run number, so every build installs as an update over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1
        versionName = "0.2.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One permanent key (GitHub secret, decoded by CI) so updates install over the previous version.
    // Local builds without it fall back to the debug key.
    val keystore = System.getenv("RELEASE_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = keystore
            storeType = "PKCS12"
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = "onlysearch"
            keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles("proguard-test-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    testBuildType = "release"

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
