import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

// Load keystore properties from project root `keystore.properties` if present.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Load GROQ API key from local.properties or environment variable
val localPropsFile = rootProject.file("local.properties")
val groqApiKey: String = run {
    try {
        if (localPropsFile.exists()) {
            val props = Properties()
            props.load(FileInputStream(localPropsFile))
            props.getProperty("GROQ_API_KEY") ?: System.getenv("GROQ_API_KEY") ?: ""
        } else {
            System.getenv("GROQ_API_KEY") ?: ""
        }
    } catch (e: Exception) {
        ""
    }
}

android {
    namespace = "com.example.remind_ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.remind_ai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Inject GROQ API key into BuildConfig safely (reads from local.properties or env)
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
    }

    buildTypes {
        signingConfigs {
            create("release") {
                // Prefer providing values in keystore.properties (not committed),
                // or via environment variables: RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
                // RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                    ?: System.getenv("RELEASE_STORE_FILE")
                    ?: System.getProperty("user.home") + "/my-release-key.jks"
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("androidx.cardview:cardview:1.0.0")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Picovoice Porcupine for wake word detection
    implementation("ai.picovoice:porcupine-android:3.0.2")

    // Lifecycle for proper activity lifecycle management
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.6.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
