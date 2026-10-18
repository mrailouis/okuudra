pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.18.2"
        id("org.jetbrains.kotlin.jvm") version "2.4.20"
    }
}

rootProject.name = "okuudra"
