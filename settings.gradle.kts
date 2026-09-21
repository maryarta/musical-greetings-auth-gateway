pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "musical-greetings-auth-gateway"

include("auth-service")
include("api-gateway")
include("demo-backend")