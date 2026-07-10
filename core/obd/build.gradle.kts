plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.revscope.core.obd"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Make main assets visible to JVM unit tests via classpath
    sourceSets {
        getByName("test") {
            resources.srcDirs("src/main/assets")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:data"))

    implementation(libs.coroutines.android)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // WorkManager — DailyStatusWorker (notificación diaria de documentos)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(libs.exp4j)
    implementation(libs.timber)

    // Embedded MCP server over local WiFi (Plan 6 Task 4)
    implementation(libs.nanohttpd)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    // org.json available on Android runtime; add for JVM unit tests
    testImplementation(libs.org.json)
}
