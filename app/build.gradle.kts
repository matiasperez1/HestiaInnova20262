import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Firebase public Android config is optional; no fabricated values or Google Services plugin.
val firebaseFile = file("google-services.json")
val firebaseConfig = if (firebaseFile.exists()) groovy.json.JsonSlurper().parse(firebaseFile) as Map<*, *> else emptyMap<Any, Any>()
val firebaseClient = (firebaseConfig["client"] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull { client ->
    ((client["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "cl.grupo28.hestia"
}
require(!firebaseFile.exists() || firebaseClient != null) { "Firebase Android package must be cl.grupo28.hestia" }
fun firebaseValue(key: String): String {
    val info = firebaseConfig["project_info"] as? Map<*, *>
    val value = when (key) {
        "APP_ID" -> (firebaseClient?.get("client_info") as? Map<*, *>)?.get("mobilesdk_app_id")
        "API_KEY" -> ((firebaseClient?.get("api_key") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")
        "PROJECT_ID" -> info?.get("project_id")
        "SENDER_ID" -> info?.get("project_number")
        else -> null
    }?.toString().orEmpty()
    require(value.matches(Regex("[A-Za-z0-9_:.\\-]*"))) { "Invalid public Firebase configuration" }
    return "\"$value\""
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
        "SUPABASE_URL", "HESTIA_API_URL" -> runCatching {
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

        buildConfigField("String", "HESTIA_API_URL", authValue("HESTIA_API_URL"))
        listOf("APP_ID", "API_KEY", "PROJECT_ID", "SENDER_ID").forEach { buildConfigField("String", "FIREBASE_$it", firebaseValue(it)) }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val demo = groovy.json.JsonSlurper().parse(rootProject.file("backend/demo.config.json")) as Map<*, *>
            val port=(demo["apiPort"] as Number).toInt()
            require(port in 1024..65535)
            buildConfigField("String","LOCAL_DEMO_API_URL","\"http://10.0.2.2:$port\"")
            buildConfigField("boolean","LOCAL_DEMO_DEFAULT",(providers.environmentVariable("LOCAL_DEMO").orNull == "true").toString())
        }
        release {
            optimization {
                enable = true
                // Scoped R8 repackaging breaks the Kotlin stdlib class hierarchy used by OkHttp.
                packageScope = setOf("androidx.**", "kotlinx.**")
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

android.sourceSets.getByName("test").resources.srcDir(rootProject.file("shared"))

dependencies {
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.google.authorization)
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
