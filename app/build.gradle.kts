plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dudo.fishing"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dudo.fishing"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "0.10.0"

        // 기상청 API 키: GitHub Secret(KMA_API_KEY) → 환경변수로 들어오거나,
        // 내 PC에서는 local.properties / gradle.properties 의 KMA_API_KEY 값을 쓴다. 코드에는 키를 적지 않는다.
        val kmaKey = System.getenv("KMA_API_KEY")
            ?: (project.findProperty("KMA_API_KEY") as String?)
            ?: ""
        buildConfigField("String", "KMA_API_KEY", "\"${kmaKey.trim()}\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // 지도: OpenStreetMap 엔진 (API 키 불필요) + Esri 위성사진 타일
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
