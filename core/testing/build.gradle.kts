// Test doubles shared by the feature modules' tests (a fake repository and sample books).
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
}
