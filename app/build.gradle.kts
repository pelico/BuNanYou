plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aicompose"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aicompose"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1"
        ndk {
            // 只打包 arm64-v8a, 大幅缩减 APK 体积 (现代安卓手机主流架构)
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        // 固定签名: 保证每次 CI 构建出的 APK 签名一致, 可直接覆盖升级
        create("release") {
            storeFile = rootProject.file("keystore/aicompose-release.jks")
            storePassword = "aicompose"
            keyAlias = "aicompose"
            keyPassword = "aicompose"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // debug 也用同一签名, 便于 debug/release 互相覆盖升级
            signingConfig = signingConfigs.getByName("release")
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 图片加载
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ONNX Runtime Mobile — 端侧推理
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    // CameraX — 实时取景
    val cameraxVersion = "1.3.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
