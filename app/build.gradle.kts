plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flowervillage.twins"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flowervillage.twins.pixel"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 앱의 HTML을 안전한 로컬 HTTPS 주소로 불러옵니다. 네트워크 연결 없이도 실행 가능.
    implementation("androidx.webkit:webkit:1.12.1")
}
