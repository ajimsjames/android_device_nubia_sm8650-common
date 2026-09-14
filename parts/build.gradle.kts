plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")

configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension> {
    namespace = "org.lineageos.settings"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.lineageos.settings"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            kotlin.srcDirs("src")
            res.srcDirs("res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.flags")
        }
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
}
