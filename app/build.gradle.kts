import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The release key: from the environment (CI secrets) or keystore.properties (local, never
// committed). Without either, a release build is left unsigned.
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(env: String, prop: String): String? = System.getenv(env) ?: keyProps.getProperty(prop)
val keystoreFile = signing("LABHUD_KEYSTORE", "storeFile")

// The version comes from the tag in CI (LABHUD_VERSION=0.1.0); 1.2.3 becomes versionCode 10203.
val version = System.getenv("LABHUD_VERSION")?.removePrefix("v") ?: "0.1.0-dev"
val code = Regex("""(\d+)\.(\d+)\.(\d+)""").find(version)?.destructured
    ?.let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() } ?: 1

android {
    namespace = "io.github.mithrandler.labhud"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mithrandler.labhud"
        minSdk = 29
        targetSdk = 36
        versionCode = code
        versionName = version
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = signing("LABHUD_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signing("LABHUD_KEY_ALIAS", "keyAlias")
                keyPassword = signing("LABHUD_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // F-Droid and IzzyOnDroid reject the encrypted dependency list Google adds to APKs.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.fragment)
    implementation(libs.datastore.preferences)
    implementation(libs.webkit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.unifiedpush)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
