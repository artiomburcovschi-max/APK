import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Переменные версии приложения перед блоком android
val vMajor = 0
val vMinor = 4
val vPatch = 0

android {
    namespace = "com.agenda.client"
    compileSdk {
        version = release(37)
    }

    // Загрузка свойств из keystore.properties для безопасной подписи
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) load(FileInputStream(f))
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.agenda.client"
        minSdk = 24
        targetSdk = 37

        // Автоматический расчет версии (0.2.0 -> 200)
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = "$vMajor.$vMinor.$vPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Подключаем созданную конфигурацию подписи ключом
            signingConfig = signingConfigs.getByName("release")

            // ОТКЛЮЧАЕМ оптимизацию и сжатие кода, чтобы не было конфликтов с Kotlin-классами
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
