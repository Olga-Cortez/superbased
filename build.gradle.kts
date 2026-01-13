// NOTE: This Gradle file exists only to help IDE/VS Code resolve symbols and provide
// syntax highlighting / autocomplete. FAST has its own build system.

plugins {
    // Android Gradle Plugin (AGP) version is required for plugin resolution.
    // If you need to change it, update only this version.
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "org.potiguaras.supabased"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("src/proguard-rules.pro")
    }

    // FAST project layout uses /src instead of the standard /src/main/... layout.
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/AndroidManifest.xml")
            java.srcDirs("src")
            kotlin.srcDirs("src")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // App Inventor runtime libraries (com.google.appinventor.*)
    implementation(fileTree("libs/fast-appinventor") { include("*.jar") })
    
    // Other local jars
    implementation(fileTree("libs") { include("*.jar") })
    implementation(fileTree("deps") { include("*.jar") })

    // Android SDK (FAST provides this)
    compileOnly(files("libs/fast-android/android.jar"))

    // Supabase Kotlin (supabase-kt) - using stable version
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    // Ktor engine for Android
    implementation("io.ktor:ktor-client-android:3.0.3")

    // KotlinX libraries
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
}