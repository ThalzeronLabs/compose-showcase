// Test helpers shared by the feature modules' tests: a fake repository, sample books and the accessibility check.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thalzeronlabs.shelf.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
    // Google's Accessibility Test Framework, made to run under Robolectric by Roborazzi.
    api(libs.roborazzi.accessibility.check)
}
