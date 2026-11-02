import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksB64 = rootProject.file("keystore.b64")
val ksFile = layout.buildDirectory.file("fixed.p12").get().asFile
val useFixedKey = ksB64.exists()
if (useFixedKey) {
    ksFile.parentFile.mkdirs()
    ksFile.writeBytes(Base64.getMimeDecoder().decode(ksB64.readText()))
}

android {
    namespace = "com.bmwcoder"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.bmwcoder"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    signingConfigs {
        if (useFixedKey) {
            create("fixed") {
                storeFile = ksFile
                storePassword = "bmwcoder"
                keyAlias = "bmwcoder"
                keyPassword = "bmwcoder"
                storeType = "pkcs12"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (useFixedKey) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
