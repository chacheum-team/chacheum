plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chacheum.app"
    compileSdk = 35

    // AAOS 표준 Car API(기어, 주행 규제 상태)를 쓰기 위해 플랫폼의 android.car 라이브러리를 붙임
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.chacheum.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Pleos SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // Vehicle SDK가 실행 중에 kotlin-reflect를 씀. Kotlin 버전과 맞춤
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")
    implementation("androidx.core:core-ktx:1.16.0")
}
