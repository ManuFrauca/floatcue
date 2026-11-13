import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.manufrauca.floatcue"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.manufrauca.floatcue"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
    }

    // Signing comes from keystore.properties (gitignored) or FLOATCUE_* env vars; without either, release stays unsigned.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? = keystoreProps.getProperty(prop) ?: System.getenv(env)
    val storePath = signingValue("storeFile", "FLOATCUE_KEYSTORE")
    val releaseSigning = storePath?.let {
        signingConfigs.create("release") {
            storeFile = rootProject.file(it)
            storePassword = signingValue("storePassword", "FLOATCUE_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "FLOATCUE_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "FLOATCUE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures { compose = true }
    // Lists English and Spanish in the system per-app language setting (Android 13+).
    androidResources { generateLocaleConfig = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
