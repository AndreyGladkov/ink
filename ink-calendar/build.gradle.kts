import java.util.Properties

plugins {
    id("com.android.application")
}

val tasksPackage = "io.github.andreygladkov.inktasks"

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.andreygladkov.inkcalendar"
    compileSdk = 36

    signingConfigs {
        val storeFile = localProperties.getProperty("signing.calendar.storeFile")
        if (storeFile != null) {
            create("github") {
                this.storeFile = file(storeFile)
                storePassword = localProperties.getProperty("signing.calendar.storePassword")
                keyAlias = localProperties.getProperty("signing.calendar.keyAlias")
                keyPassword = localProperties.getProperty("signing.calendar.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.andreygladkov.inkcalendar"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "TASKS_PACKAGE", "\"$tasksPackage\"")
        manifestPlaceholders["tasksPackage"] = tasksPackage
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("github")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "TASKS_PACKAGE", "\"$tasksPackage.debug\"")
            manifestPlaceholders["tasksPackage"] = "$tasksPackage.debug"
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
    archivesName.set("InkCalendar-${android.defaultConfig.versionName}")
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
