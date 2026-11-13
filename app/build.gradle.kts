plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// release 서명 정보는 환경 변수로만 받음(GitHub Actions가 Secrets에서 넣음). 레포와 파일에는 두지 않음.
// RELEASE_KEYSTORE_FILE이 없으면 서명 없는 release(app-release-unsigned.apk)가 나옴.
val releaseKeystoreFile: String? = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

fun releaseSigningEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("release 서명: 환경 변수 $name 이 비어 있음")

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

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = releaseSigningEnv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
    // 화면 캡처 테스트(Robolectric)에서 앱 리소스·글꼴을 씀
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("chacheum.shots", project.layout.buildDirectory.dir("screens").get().asFile.path)
                System.getProperty("robolectric.dependency.repo.url")?.let { url -> it.systemProperty("robolectric.dependency.repo.url", url) }
            }
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

    // 화면 캡처 테스트: JVM에서 화면을 그려 PNG로 남김
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
