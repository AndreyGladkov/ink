import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.andreygladkov.inktasks"
    compileSdk = 36

    signingConfigs {
        val storeFile = localProperties.getProperty("signing.tasks.storeFile")
        if (storeFile != null) {
            create("github") {
                this.storeFile = file(storeFile)
                storePassword = localProperties.getProperty("signing.tasks.storePassword")
                keyAlias = localProperties.getProperty("signing.tasks.keyAlias")
                keyPassword = localProperties.getProperty("signing.tasks.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.andreygladkov.inktasks"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
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
        localeFilters += listOf("en", "ru", "de", "fr", "es", "it", "pl")
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
    archivesName.set("InkTasks-${android.defaultConfig.versionName}")
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
