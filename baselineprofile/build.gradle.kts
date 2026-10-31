plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Records which code the app runs at startup and on each tab, so Android can
// compile it ahead of time. Run it with the "Generate Baseline Profile" run
// configuration in Android Studio, or `./gradlew :app:generateBaselineProfile`.
android {
    namespace = "com.omnituner.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

// Use the connected emulator or phone, not a Gradle-managed device.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
