
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.fintrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fintrack"

        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // Các môi trường của ứng dụng
    flavorDimensions += "environment"

    productFlavors {
        create("mock") {
            dimension = "environment"
            applicationIdSuffix = ".mock"

            buildConfigField(
                "boolean",
                "USE_MOCK",
                "true"
            )
            buildConfigField(
                "String",
                "BASE_URL",
                "\"http://10.0.2.2:8080/\""
            )
        }

        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"

            buildConfigField(
                "boolean",
                "USE_MOCK",
                "false"
            )
            buildConfigField(
                "String",
                "BASE_URL",
                "\"http://10.0.2.2:8080/\""
            )
        }

        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"

            buildConfigField(
                "boolean",
                "USE_MOCK",
                "false"
            )
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://api-staging.example.com/\""
            )
        }

        create("prod") {
            // Không thêm applicationIdSuffix để giữ ID phát hành
            buildConfigField(
                "boolean",
                "USE_MOCK",
                "false"
            )
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://api.example.com/\""
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            // Giữ cấu hình debug mặc định
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Android UI
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    implementation(libs.material)

    // Networking
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Security: giữ nếu dự án đang sử dụng
    implementation(libs.security.crypto)

    // Unit testing
    testImplementation(libs.junit)

    // Android instrumentation testing
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
