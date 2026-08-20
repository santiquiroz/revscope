plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.revscope.core.maps"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    // Trae androidx.lifecycle.compose.LocalLifecycleOwner: el de compose.ui.platform está
    // deprecado y este módulo es nuevo, así que arranca con la API vigente.
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.maplibre)
    implementation(libs.maplibre.turf)
    implementation(libs.timber)
    testImplementation(libs.junit)
    // org.json existe en el runtime de Android pero no en el classpath de tests JVM.
    testImplementation(libs.org.json)
}

kotlin {
    // Kotlin 2.3 elimino el DSL kotlinOptions; jvmTarget vive en compilerOptions.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
