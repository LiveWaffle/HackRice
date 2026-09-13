import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.AMMR.ricehacks"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        val backendBaseUrl = localProperties.getProperty("BACKEND_BASE_URL")
            ?: providers.gradleProperty("BACKEND_BASE_URL").getOrNull()
            ?: providers.environmentVariable("BACKEND_BASE_URL").getOrNull()
            ?: "http://10.0.2.2:5000"
        val presageApiKey = localProperties.getProperty("PRESAGE_API_KEY")
            ?: providers.gradleProperty("PRESAGE_API_KEY").getOrNull()
            ?: providers.environmentVariable("PRESAGE_API_KEY").getOrNull()
            ?: ""
        // ELEVENLABS_AGENT_ID is safe to keep in local.properties (client-side/public metadata).
        // ELEVENLABS_API_KEY must stay server-side in Supabase Edge Function secrets, not in Android app config.
        val elevenLabsAgentId = localProperties.getProperty("ELEVENLABS_AGENT_ID")
            ?: providers.gradleProperty("ELEVENLABS_AGENT_ID").getOrNull()
            ?: providers.environmentVariable("ELEVENLABS_AGENT_ID").getOrNull()
            ?: ""

        applicationId = "com.AMMR.ricehacks"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://hgjreiiimbjbkqflmwte.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_GfzBoxNDFkAOIFmAxLDBdg_PhP4JX_Q\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "PRESAGE_API_KEY", "\"$presageApiKey\"")
        buildConfigField("String", "ELEVENLABS_AGENT_ID", "\"$elevenLabsAgentId\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.camera:camera-view:1.6.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("com.presagetech:smartspectra:3.3.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.zxing.core)
    implementation(libs.elevenlabs.agents)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
