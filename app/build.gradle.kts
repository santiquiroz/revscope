plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.revscope.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.revscope.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.9.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sideload distribution via GitHub Releases — debug-keystore signing
            // is intentional until a proper release keystore exists.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:obd"))
    implementation(project(":core:intelligence"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:gear"))
    implementation(project(":feature:sensors"))
    implementation(project(":feature:dtc"))
    implementation(project(":feature:session"))
    implementation(project(":feature:vehicle"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auto"))
    implementation(project(":feature:workshop"))
    implementation(project(":feature:map"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.google.fonts)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager — Configuration.Provider con HiltWorkerFactory para DailyStatusWorker
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    // DataStore — settingsDataStore in IntelligenceModule
    implementation(libs.datastore.preferences)

    // Galaxy Watch heart-rate stream (Data Layer)
    implementation(libs.play.services.wearable)

    // Timber
    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
