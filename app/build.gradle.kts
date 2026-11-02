plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.ufuq.dream"
    compileSdk = 34
    defaultConfig { applicationId = "com.ufuq.dream"; minSdk = 28; targetSdk = 34; versionCode = 14; versionName = "0.8.2" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    androidResources { noCompress += "mp4" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.14.1") }
