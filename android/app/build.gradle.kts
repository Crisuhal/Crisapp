plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "ar.com.crismultimedia"
    compileSdk = 34
    defaultConfig {
        applicationId = "ar.com.crismultimedia"
        minSdk = 23
        targetSdk = 28
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
}
