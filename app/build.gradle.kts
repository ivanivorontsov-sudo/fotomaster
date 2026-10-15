import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.ivanivorontsov.fotomaster"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.ivanivorontsov.fotomaster"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += ((project.findProperty("abi") as String?) ?: "arm64-v8a").split(",") }
    }

    signingConfigs {
        create("release") {
            val path = signProps.getProperty("storeFile")
            if (path != null) {
                storeFile = file(path)
                storePassword = signProps.getProperty("storePassword")
                keyAlias = signProps.getProperty("keyAlias")
                keyPassword = signProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signProps.getProperty("storeFile") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") } }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    implementation("com.google.mlkit:face-detection:16.1.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.json:json:20240303")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
