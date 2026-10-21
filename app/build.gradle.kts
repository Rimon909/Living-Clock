plugins {
    id("com.android.application")
}

android {
    namespace = "com.rimon.livingclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rimon.livingclock"
        minSdk = 23
        targetSdk = 34

        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
