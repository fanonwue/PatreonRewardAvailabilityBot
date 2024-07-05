import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
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
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.7.1")

    implementation("io.ktor", "ktor-client-core", "2.3.12")
    implementation("io.ktor", "ktor-client-cio", "2.3.12")
    implementation("io.ktor", "ktor-client-content-negotiation", "2.3.12")
    implementation("io.ktor", "ktor-serialization-kotlinx-json", "2.3.12")

    implementation("org.jetbrains.exposed", "exposed-core", "0.52.0")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.52.0")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.52.0")
    implementation("org.jetbrains.exposed", "exposed-java-time", "0.52.0")

    implementation("org.xerial", "sqlite-jdbc", "3.46.0.0")

    implementation("dev.inmo", "tgbotapi", "15.0.0")

    implementation("io.github.oshai", "kotlin-logging-jvm", "7.0.0")
    implementation("org.apache.logging.log4j", "log4j-slf4j2-impl", "2.23.1")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.9.2")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("de.arisendrake.patreonrewardavailabilitybot.MainKt")
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
    }

    named<Jar>("jar") {
        manifest {
            // Log4j2 requires this for better performance
            // https://issues.apache.org/jira/browse/LOG4J2-2537
            attributes["Multi-Release"] = true
        }
    }

    named<ShadowJar>("shadowJar"){
        mergeServiceFiles()
        archiveFileName.set("patreon-availability-bot.jar")
    }
}
