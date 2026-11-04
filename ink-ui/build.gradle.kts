plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.andreygladkov.ink"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testFixtures {
        enable = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(libs.androidx.core)

    testFixturesImplementation(libs.androidx.core)
    testFixturesImplementation(libs.robolectric)
    testFixturesImplementation(kotlin("test"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
