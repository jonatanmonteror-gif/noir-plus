plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.noirplus.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noirplus.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Firma:
    //  - Clave nueva (recomendada): app/noirplus.jks.enc, descifrada por GitHub Actions con el secreto NOIR_KEY_PASSWORD.
    //  - Mientras ese secreto no exista, se sigue usando la clave antigua (legacy.jks) para no obligar a reinstalar.
    val newKey = file("noirplus.jks")
    val newPass = System.getenv("NOIR_KEY_PASSWORD")
    val useNewKey = newKey.exists() && !newPass.isNullOrEmpty()
    signingConfigs {
        create("release") {
            if (useNewKey) {
                storeFile = newKey
                storePassword = newPass
                keyAlias = "noirplus"
                keyPassword = newPass
            } else {
                storeFile = file("legacy.jks")
                storePassword = "nexotv2026"
                keyAlias = "nexotv"
                keyPassword = "nexotv2026"
            }
        }
    }

    buildTypes {
        release {
            // App más ligera: R8 elimina código no usado y comprime; shrinkResources quita recursos sobrantes.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin", "META-INF/com.android.tools/**")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")
}
