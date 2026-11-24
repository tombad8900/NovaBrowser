plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.nova.browser"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.nova.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.libtorrent4j:libtorrent4j-linux:2.1.0-39")
}

android.testOptions.unitTests.all {
    it.jvmArgs("-Djava.library.path=" + (System.getenv("NOVA_NATIVE_DIR") ?: ""))
    it.testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
