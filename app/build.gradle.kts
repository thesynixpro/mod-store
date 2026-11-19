import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Optional release signing. `release.properties` is machine-local and git-ignored, so a
// checkout without it still configures: the release build type simply stays unsigned.
val keystorePropsFile = rootProject.file("release.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
}
val hasValidSigningProps = keystorePropsFile.exists() &&
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !keystoreProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.aprax.modstore"
    compileSdk = 37

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    defaultConfig {
        applicationId = "com.aprax.modstore"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }

        // Source endpoints and legal contacts are declared once here so every module reads
        // the same values through BuildConfig rather than hard-coding them.
        buildConfigField("String", "SOURCE_MODYOLO_URL", "\"https://modyolo.com\"")
        buildConfigField("String", "SOURCE_HAPPYMOD_URL", "\"https://happymod.com\"")
        buildConfigField("String", "SOURCE_A1N_URL", "\"https://a1n.com\"")
        buildConfigField("String", "TAKEDOWN_EMAIL", "\"takedown@aprax.example\"")
        buildConfigField("String", "TERMS_URL", "\"https://aprax.example/mods-store/terms\"")
        buildConfigField("String", "PRIVACY_URL", "\"https://aprax.example/mods-store/privacy\"")
    }

    signingConfigs {
        if (hasValidSigningProps) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            if (hasValidSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/kotlinx_coroutines_core.version"

            // The on-device/local Maven repository ships KMP metadata jars that collide on
            // these paths. Picking the first copy is safe for an Android-only build and is
            // what makes dependency resolution succeed offline.
            pickFirsts += "nonJvmMain/default/linkdata/package_androidx/0_androidx.knm"
            pickFirsts += "nonJvmMain/default/linkdata/root_package/0_.knm"
            pickFirsts += "nonJvmMain/default/linkdata/module"
            pickFirsts += "nativeMain/default/linkdata/root_package/0_.knm"
            pickFirsts += "nativeMain/default/linkdata/module"
            pickFirsts += "commonMain/default/linkdata/root_package/0_.knm"
            pickFirsts += "commonMain/default/linkdata/module"
            pickFirsts += "commonMain/default/linkdata/package_androidx/0_androidx.knm"
            pickFirsts += "META-INF/kotlin-project-structure-metadata.json"

            merges += "commonMain/default/manifest"
            merges += "nonJvmMain/default/manifest"
            merges += "nativeMain/default/manifest"
            merges += "jvmMain/default/manifest"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(project(":feature-home"))
    implementation(project(":feature-categories"))
    implementation(project(":feature-search"))
    implementation(project(":feature-details"))
    implementation(project(":feature-downloads"))
    implementation(project(":feature-library"))
    implementation(project(":feature-settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
}
