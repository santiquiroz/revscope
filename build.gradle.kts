plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

// MapLibre 13.4.1 y su kotlin-stdlib vienen compilados con Kotlin 2.2 y el proyecto compila
// con 2.0.21, así que el compilador se niega a leer su metadata. Va a nivel raíz porque lo
// necesita todo módulo que compile contra MapLibre, no solo :core:maps.
//
// Medido, no asumido: subir Kotlin no es opción todavía. 2.2.21 rompe kapt en :core:obd
// ("unsupported metadata kind: null" en las clases @HiltWorker, porque
// androidx.hilt:hilt-compiler:1.2.0 no lee metadata 2.2) y 2.3.21 rompe además kapt en
// :core:data y elimina el DSL kotlinOptions. Salir de acá exige migrar Room y Hilt de kapt a
// KSP: es tarea cero de la Fase 3, donde Ferrostar exigirá stdlib 2.3.x de todas formas.
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
}
