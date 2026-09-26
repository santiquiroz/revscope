plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.revscope.feature.map"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

roborazzi { outputDir.set(file("src/test/screenshots")) }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:obd"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core:maps"))
    api(project(":core:navigation"))
    implementation(libs.maplibre)
    implementation(libs.timber)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    // org.json existe en el runtime de Android pero no en el classpath de tests JVM.
    testImplementation(libs.org.json)
    testImplementation(project(":core:ui-testing"))
}

kotlin {
    // Kotlin 2.3 elimino el DSL kotlinOptions; jvmTarget vive en compilerOptions.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
