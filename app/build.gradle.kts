plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.greatwy8888.servicecountdown"; compileSdk = 35
    defaultConfig { applicationId = "com.greatwy8888.servicecountdown"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
