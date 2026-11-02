plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.thalzeronlabs.shelf"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.thalzeronlabs.shelf"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
    }

    // The release workflow generates a throwaway demo key and passes it in through these variables.
    // Locally (and in CI on pull requests) they are absent, so release builds fall back to the debug key.
    val releaseKeystore = providers.environmentVariable("SHELF_KEYSTORE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SHELF_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SHELF_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SHELF_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric (screenshots, Compose UI tests) needs the merged resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's Android 16 (API 36) runtime reaches into this JDK package.
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

baselineProfile {
    // The profile is generated on an emulator by the "Baseline profile" workflow and committed to
    // src/release/generated/baselineProfiles, so ordinary builds (and CI on pull requests) never need a device.
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:search"))
    implementation(project(":feature:shelf"))
    implementation(project(":sheets"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    // Installs the baseline profile on devices that do not get it from Play (sideloaded APKs like ours).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.paging.compose)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
