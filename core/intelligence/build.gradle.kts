plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.revscope.core.intelligence"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AdaptiveGearLearner necesita VehicleType (marchas por perfil, Task 4) — core:obd
    // depende de core:data via implementation (no transitivo), así que hace falta directo.
    implementation(project(":core:data"))
    implementation(project(":core:obd"))

    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    // org.json available on Android runtime; add for JVM unit tests (AiResponseParsersTest)
    testImplementation(libs.org.json)
}

kotlin {
    // Kotlin 2.3 elimino el DSL kotlinOptions; jvmTarget vive en compilerOptions.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
