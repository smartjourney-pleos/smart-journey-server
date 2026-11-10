plugins {
    // JDK 21 이 없으면 Gradle 이 자동으로 내려받도록 (toolchain auto-provisioning)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "smart-journey-server"
