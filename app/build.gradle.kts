plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.empowerswr.luksave"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.empowerswr.luksave"
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

    ndkVersion = "29.0.13599879-rc2"

    packaging {
        resources {
            excludes.addAll(listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/ASL2.0",
                "META-INF/LGPL2.1",
                "META-INF/FastDoubleParser-LICENSE",
                "META-INF/io.netty.versions.properties",
                "META-INF/INDEX.LIST"
            ))
        }
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
    implementation(libs.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.play.services.location)
    implementation(libs.pdf.viewer)
    implementation(libs.signature.pad)
    implementation(libs.itext7.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.coil.compose)
    implementation(libs.s3) {
        exclude(group = "software.amazon.awssdk", module = "third-party-jackson-core")
    }
    implementation(libs.jackson.core)

}