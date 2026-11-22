// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Version de Kotlin : le plugin Android 9 compile le Kotlin lui-même
// (« built-in Kotlin ») avec Kotlin 2.2.10 par défaut. Pour une version plus
// récente, la documentation du plugin Android (notes de version d'AGP 9.0,
// « Runtime dependency on Kotlin Gradle plugin ») indique d'ajouter le plugin
// Kotlin ici, avec la version voulue. Elle doit être IDENTIQUE à « kotlin »
// dans gradle/libs.versions.toml (décision 55).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
