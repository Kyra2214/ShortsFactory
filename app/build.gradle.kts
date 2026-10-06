plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shortsfactory.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shortsfactory.app"
        minSdk = 26
        targetSdk = 35
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libffmpeg.so"
            keepDebugSymbols += "**/libffprobe.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":video-engine"))
    implementation(project(":feature-home"))
    implementation(project(":feature-projects"))
    implementation(project(":feature-editor"))
    implementation(project(":feature-ai"))
    implementation(project(":feature-export"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-trends"))
    implementation(project(":feature-player"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
