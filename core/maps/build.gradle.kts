plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.revscope.core.maps"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // MapLibre 13.4.1 y su kotlin-stdlib vienen compilados con Kotlin 2.2 y el proyecto
        // compila con 2.0.21. Sin esto el compilador rechaza leer su metadata. Provisional:
        // la salida real es subir Kotlin (Ferrostar exigirá 2.3.x en la Fase 3).
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.maplibre)
    implementation(libs.maplibre.turf)
    implementation(libs.timber)
    testImplementation(libs.junit)
}
