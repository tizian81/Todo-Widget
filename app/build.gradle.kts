plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.tizian.todowidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.tizian.todowidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.4.1"
    }

    // Fester Schlüssel, damit spätere Versionen über die alte installiert werden können
    signingConfigs {
        create("fixed") {
            storeFile = file("signing.keystore")
            storePassword = "todowidget"
            keyAlias = "todowidget"
            keyPassword = "todowidget"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
