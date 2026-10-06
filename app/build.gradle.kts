import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val signingValues = listOf("SHABBOS_KEYSTORE_PATH", "SHABBOS_KEYSTORE_PASSWORD",
    "SHABBOS_KEY_ALIAS", "SHABBOS_KEY_PASSWORD").associateWith {
    providers.environmentVariable(it).orNull
}
val hasReleaseSigning = signingValues.values.all { !it.isNullOrBlank() }
require(signingValues.values.all { it.isNullOrBlank() } || hasReleaseSigning) {
    "Provide all four SHABBOS signing environment variables."
}

android {
    namespace = "dev.dovie.shabbosmode"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.dovie.shabbosmode"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
    }

    if (hasReleaseSigning) signingConfigs.create("release") {
        storeFile = file(signingValues.getValue("SHABBOS_KEYSTORE_PATH")!!)
        storePassword = signingValues.getValue("SHABBOS_KEYSTORE_PASSWORD")
        keyAlias = signingValues.getValue("SHABBOS_KEY_ALIAS")
        keyPassword = signingValues.getValue("SHABBOS_KEY_PASSWORD")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast { check(hasReleaseSigning) { "Release builds require the SHABBOS signing environment variables." } }
}
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    testImplementation("junit:junit:4.13.2")
}
