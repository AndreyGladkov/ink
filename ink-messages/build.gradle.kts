import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.andreygladkov.inkmessages"
    compileSdk = 36

    signingConfigs {
        val storeFile = localProperties.getProperty("signing.messages.storeFile")
        if (storeFile != null) {
            create("github") {
                this.storeFile = file(storeFile)
                storePassword = localProperties.getProperty("signing.messages.storePassword")
                keyAlias = localProperties.getProperty("signing.messages.keyAlias")
                keyPassword = localProperties.getProperty("signing.messages.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.andreygladkov.inkmessages"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("github")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
    }
}

base {
    archivesName.set("InkMessages-${android.defaultConfig.versionName}")
}

dependencies {
    implementation(project(":ink-ui"))
    implementation(libs.androidx.core)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(testFixtures(project(":ink-ui")))
}
