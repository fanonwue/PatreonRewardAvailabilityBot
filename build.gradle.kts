import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "de.arisendrake"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.5.0")

    implementation("io.ktor", "ktor-client-core", "2.3.0")
    implementation("io.ktor", "ktor-client-cio", "2.3.0")
    implementation("io.ktor", "ktor-client-content-negotiation", "2.3.0")
    implementation("io.ktor", "ktor-serialization-kotlinx-json", "2.3.0")

    implementation("org.jetbrains.exposed", "exposed-core", "0.41.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.41.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.41.1")
    implementation("org.jetbrains.exposed", "exposed-java-time", "0.41.1")

    implementation("org.xerial", "sqlite-jdbc", "3.41.2.1")


    implementation("dev.inmo", "tgbotapi", "7.1.0")

    implementation("io.github.microutils", "kotlin-logging-jvm", "3.0.5")
    implementation("org.apache.logging.log4j", "log4j-slf4j2-impl", "2.20.0")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.9.2")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
    }

    named<ShadowJar>("shadowJar"){
        mergeServiceFiles()
        archiveFileName.set("patreon-availability-bot.jar")
    }
}


application {
    mainClass.set("de.arisendrake.patreonrewardavailabilitybot.MainKt")
}
