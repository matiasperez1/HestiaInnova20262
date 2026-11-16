import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val authProperties = Properties().apply {
    val source = rootProject.file("secrets.properties")
    if (source.exists()) source.inputStream().use { load(it) }
}
fun authValue(name: String): String {
    val value = providers.environmentVariable(name).orNull ?: authProperties.getProperty(name, "")
    require(value.isBlank() || when (name) {
        "SUPABASE_PUBLISHABLE_KEY" -> value.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))
        "GOOGLE_WEB_CLIENT_ID" -> value.endsWith(".apps.googleusercontent.com")
        "SUPABASE_URL" -> runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/")
        }.getOrDefault(false)
        else -> false
    }) { "Invalid public auth configuration: $name. Never use administrative secrets in Android." }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
}

android {
    namespace = "cl.grupo28.hestia"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cl.grupo28.hestia"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", authValue("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", authValue("SUPABASE_PUBLISHABLE_KEY"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", authValue("GOOGLE_WEB_CLIENT_ID"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
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
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.google)
    implementation(libs.google.id)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.json.test)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
