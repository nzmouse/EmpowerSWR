import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("17"))  // Replace "17" with your value
    }
}
android {
    namespace = "com.empowerswr.test"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.empowerswr.test"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.0.21"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.retrofit)
    implementation(libs.bundles.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.java.jwt)
    implementation (libs.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit.converter.scalars)

    debugImplementation(libs.androidx.compose.ui.tooling)
}