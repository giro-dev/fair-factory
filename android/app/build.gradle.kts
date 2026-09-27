plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "dev.agiro.fanel.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.agiro.fanel.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 109
        versionName = "0.1.9-SNAPSHOT"

        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
    }

    signingConfigs {
        create("release") {
            fun envOr(name: String, fallback: String) =
                System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback
            // Self-signed dev key committed at android/app/selfsigned.jks.
            // CI can override all of it via FANEL_KEYSTORE* env vars/secrets.
            storeFile = envOr("FANEL_KEYSTORE", "").takeIf { it.isNotBlank() }
                ?.let(::File) ?: file("selfsigned.jks")
            storePassword = envOr("FANEL_KEYSTORE_PASSWORD", "fanel-selfsigned")
            keyAlias = envOr("FANEL_KEY_ALIAS", "fanel")
            keyPassword = envOr("FANEL_KEY_PASSWORD", "fanel-selfsigned")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-gson:2.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.41.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3-android:0.41.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.work:work-testing:2.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
