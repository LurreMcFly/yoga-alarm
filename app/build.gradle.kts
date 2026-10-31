buildscript {
    if (providers.gradleProperty("monetizationAnalyticsEnabled").orNull == "true") {
        check(providers.gradleProperty("monetizationPrivacyApproved").orNull == "true") {
            "Analytics requires -PmonetizationPrivacyApproved=true after privacy/store disclosures are reviewed."
        }
        check(file("google-services.json").isFile) {
            "Analytics requires the real app/google-services.json from the configured Firebase project."
        }
        repositories { google(); mavenCentral() }
        dependencies { classpath("com.google.gms:google-services:4.5.0") }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val monetizationAnalyticsEnabled = providers.gradleProperty("monetizationAnalyticsEnabled").orNull == "true"
val proTrialRolloutEnabled = providers.gradleProperty("proTrialRolloutEnabled").orNull == "true"
val proTrialInternalTesting = providers.gradleProperty("proTrialInternalTesting").orNull == "true"
if (monetizationAnalyticsEnabled) {
    apply(plugin = "com.google.gms.google-services")
}
if (proTrialRolloutEnabled) {
    check(monetizationAnalyticsEnabled && providers.gradleProperty("monetizationBillingReady").orNull == "true") {
        "Trial rollout requires configured measurement and -PmonetizationBillingReady=true after trusted billing/device acceptance."
    }
}

android {
    namespace = "com.lurremcfly.yogaalarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lurremcfly.yogaalarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "1.0.31"
        buildConfigField("boolean", "MONETIZATION_ANALYTICS_ENABLED", monetizationAnalyticsEnabled.toString())
        buildConfigField("boolean", "PRO_TRIAL_ROLLOUT_ENABLED", (proTrialRolloutEnabled || proTrialInternalTesting).toString())
        buildConfigField("boolean", "PRO_TRIAL_INTERNAL_TESTING", proTrialInternalTesting.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").java.srcDir(
        if (monetizationAnalyticsEnabled) "src/analyticsEnabled/java" else "src/analyticsDisabled/java",
    )
    if (!monetizationAnalyticsEnabled) {
        sourceSets.getByName("debug").manifest.srcFile("src/analyticsDisabled/AndroidManifest.xml")
        sourceSets.getByName("release").manifest.srcFile("src/analyticsDisabled/AndroidManifest.xml")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    androidResources {
        noCompress += "task"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    if (monetizationAnalyticsEnabled) {
        implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
        implementation("com.google.firebase:firebase-analytics")
    }
    val cameraXVersion = "1.4.2"
    val lifecycleVersion = "2.8.7"

    constraints {
        implementation("androidx.fragment:fragment:1.8.9") {
            because("CameraX and Play services otherwise resolve the outdated Fragment 1.1.0")
        }
    }

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
