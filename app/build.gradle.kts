import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.andreygladkov.inkphone"
    compileSdk = 36

    signingConfigs {
        listOf("github").forEach { name ->
            val storeFile = localProperties.getProperty("signing.$name.storeFile") ?: return@forEach
            create(name) {
                this.storeFile = file(storeFile)
                storePassword = localProperties.getProperty("signing.$name.storePassword")
                keyAlias = localProperties.getProperty("signing.$name.keyAlias")
                keyPassword = localProperties.getProperty("signing.$name.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.andreygladkov.inkphone"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("github")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    archivesName.set("InkPhone-${android.defaultConfig.versionName}")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1") {
        // Only plain vCard text is read and written; jCard, xCard and hCard support is not needed.
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "org.freemarker")
        exclude(group = "org.jsoup")
    }

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
}
