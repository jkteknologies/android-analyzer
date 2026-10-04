// Root build file. Plugin versions are resolved from gradle/libs.versions.toml.
// AGP 9.x has built-in Kotlin support: no standalone Kotlin Android plugin is declared
// (R-01). The Compose compiler plugin is applied versionless and resolved by AGP's
// built-in Kotlin (required since Kotlin 2.0 whenever compose is enabled).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
