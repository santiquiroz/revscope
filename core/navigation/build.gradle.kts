plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.revscope.core.navigation"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Ferrostar es Rust: solo hay .so de Android, así que lo que lo toca corre en el
        // dispositivo. Mismas ABI que el APK, para no empaquetar x86 que no se usa.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    sourceSets["androidTest"].assets.srcDir("src/androidTest/assets")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Obligatorio: los AAR de Ferrostar lo declaran en su metadata.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.ferrostar.core)
    implementation(libs.coroutines.android)
    // Solo la anotación: este módulo no necesita el grafo de Hilt, solo declararse inyectable.
    implementation(libs.javax.inject)
    implementation(libs.timber)
    testImplementation(libs.junit)
    // org.json existe en el runtime de Android pero no en el classpath de tests JVM.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
