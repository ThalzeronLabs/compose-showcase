// Macrobenchmark and Baseline Profile generation for :app. Runs on a device or emulator, never on the JVM:
//
//   ./gradlew :app:generateBaselineProfile             # writes app/src/release/generated/baselineProfiles
//   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   # startup timing on a connected device
//
// The "Baseline profile" workflow does both on a Gradle-managed emulator (the device defined below).
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.thalzeronlabs.shelf.benchmark"
    compileSdk = 37

    defaultConfig {
        // Baseline Profile generation needs API 28+ (33+ without root); Macrobenchmark needs 23+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
