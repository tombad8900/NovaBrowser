plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.nova.browser"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.nova.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0-gecko"
    }
    flavorDimensions += "abi"
    productFlavors {
        create("arm64") { dimension = "abi"; ndk { abiFilters += listOf("arm64-v8a") } }
        create("x64") { dimension = "abi"; ndk { abiFilters += listOf("x86_64") } }
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

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    val gv = "160.0.20261116130455"
    "arm64Implementation"("org.mozilla.geckoview:geckoview-arm64-v8a:$gv")
    "x64Implementation"("org.mozilla.geckoview:geckoview-x86_64:$gv")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.libtorrent4j:libtorrent4j-linux:2.1.0-39")
}

android.testOptions.unitTests.all {
    it.jvmArgs("-Djava.library.path=" + (System.getenv("NOVA_NATIVE_DIR") ?: ""))
    it.testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
