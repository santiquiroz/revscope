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
}

kotlin {
    // Kotlin 2.3 elimino el DSL kotlinOptions; jvmTarget vive en compilerOptions.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // MapLibre 13.4.1 y su kotlin-stdlib vienen compilados con Kotlin 2.2 y el proyecto
        // compila con 2.0.21, así que el compilador se niega a leer su metadata.
        //
        // Medido: subir Kotlin no es opción todavía. 2.2.21 rompe kapt en :core:obd
        // ("Unable to read Kotlin metadata due to unsupported metadata kind: null" en las
        // clases @HiltWorker: androidx.hilt:hilt-compiler:1.2.0 no lee metadata 2.2) y
        // 2.3.21 rompe además kapt en :core:data. Salir de acá exige migrar Room y Hilt de
        // kapt a KSP, que es tarea cero de la Fase 3 (Ferrostar exigirá stdlib 2.3.x).
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
