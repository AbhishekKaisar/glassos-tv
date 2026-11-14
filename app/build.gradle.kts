import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The TMDB API key (v3) comes from local.properties (tmdb.apiKey=...) or the TMDB_API_KEY environment variable.
// It is never committed. Without one, Glass shows its built-in scenes (a key can also be entered in Settings).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// A maintainer can also keep it outside the repository in ~/.glass-release/tmdb.properties (tmdb.apiKey=...).
val tmdbProps = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.glass-release/tmdb.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbKey: String = (localProps.getProperty("tmdb.apiKey") ?: System.getenv("TMDB_API_KEY")
    ?: tmdbProps.getProperty("tmdb.apiKey") ?: "").trim()

// Release signing is read from a file outside the repository (see CONTRIBUTING.md). Without it, release
// builds are unsigned.
val keystorePropsFile = file(System.getenv("GLASS_KEYSTORE_PROPERTIES") ?: "${System.getProperty("user.home")}/.glass-release/keystore.properties")
val keystoreProps = Properties().apply { if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) } }

android {
    namespace = "io.github.abhishekkaisar.glassos"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "io.github.abhishekkaisar.glassos"
        minSdk = 17
        targetSdk = 28
        versionCode = 3
        versionName = "1.0.2-beta"
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKey.replace("\"", "")}\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
