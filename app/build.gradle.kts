plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.ufuq.dream"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ufuq.dream"; minSdk = 28; targetSdk = 34; versionCode = 34; versionName = "0.12.0-dev2"
        val sourceCommit = providers.environmentVariable("UFUQ_SOURCE_COMMIT").orElse("development").get()
        require(sourceCommit == "development" || sourceCommit.matches(Regex("[0-9a-f]{40}")))
        resValue("string", "source_commit", sourceCommit)
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    androidResources { noCompress += "mp4" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("com.batoulapps.adhan:adhan:1.2.1"); testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.17") }
