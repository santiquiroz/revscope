plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Utilidades de captura para los tests JVM de los demás módulos (testImplementation).
android {
    namespace = "com.revscope.core.uitesting"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:designsystem"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.activity.compose)
    api(libs.compose.ui.test.junit4)
    api(libs.compose.ui.test.manifest)
    api(libs.junit)
    api(libs.robolectric)
    api(libs.androidx.test.core)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
