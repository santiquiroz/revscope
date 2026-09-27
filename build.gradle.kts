plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Las capturas Roborazzi de pantallas largas (la sesión del Taller) agotaban de forma intermitente los 512 MB
// por defecto del worker de tests en la corrida completa (OutOfMemoryError al rasterizar).
subprojects {
    tasks.withType<Test>().configureEach { maxHeapSize = "1g" }
}
