// Top-level build file
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    // Hilt 2.53 is the first release that reads Kotlin 2.1.x metadata cleanly.
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    // KSP replaces kapt for Hilt — faster, and avoids the Kotlin-2.1 / kapt metadata issue.
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
