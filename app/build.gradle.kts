plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.apptest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.apptest"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Base URL de Xano (unificada)
        buildConfigField("String", "XANO_BASE_URL", "\"https://x8ki-letl-twmt.n7.xano.io/api:cGjNNLgz/\"")
        buildConfigField("String", "XANO_AUTH_BASE_URL", "\"https://x8ki-letl-twmt.n7.xano.io/api:NUzxXGzL/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Upgrade Java source/target compatibility to Java 21
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        // Align Kotlin JVM target with Java 21
        jvmTarget = "21"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// Ensure Kotlin uses JDK 21 via toolchains (Gradle will auto-download if needed)
kotlin {
    jvmToolchain(21)
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // retofit - FIXED
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    // OkHttp
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    // Gson
    implementation(libs.gson)
    // Coil for image loading
    implementation(libs.coil)
    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    // Activity KTX
    implementation(libs.activity.ktx)
    // UI components
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.cardview)
    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}
