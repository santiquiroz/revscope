plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.revscope.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}

kotlin {
    // Kotlin 2.3 elimino el DSL kotlinOptions; jvmTarget vive en compilerOptions.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
