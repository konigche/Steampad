pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.isxander.dev/releases")
    }

    // Plugin versions live here so build.gradle.kts can apply them without repeating a version.
    plugins {
        id("dev.kikugie.stonecutter") version "0.8.2"
        id("dev.isxander.modstitch.base") version "0.8.4"
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

// One Gradle subproject per <mcVersion>-<loader> combination. They all share the single src/ tree
// and build.gradle.kts; per-variant differences live in versions/<variant>/gradle.properties and in
// Stonecutter `//? if` comments in the sources. Matrix: versions/versions.json.
stonecutter {
    create(rootProject, file("versions/versions.json"))
}

rootProject.name = "steampad"
