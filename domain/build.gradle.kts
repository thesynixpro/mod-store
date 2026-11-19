plugins {
    alias(libs.plugins.android.library)
}

// The domain layer holds the application model, repository contracts and use cases.
// It is a plain library with no Compose or Android framework usage so that it stays
// trivially unit-testable and safe for every other module to depend on.
android {
    namespace = "com.aprax.modstore.domain"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    // `javax.inject` (the JDK annotation, not an Android dependency) marks use-case
    // constructors so Hilt can satisfy the feature view models that consume them.
    api("javax.inject:javax.inject:1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
