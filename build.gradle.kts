import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

group = "de.arisendrake"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")

    implementation("io.ktor:ktor-client-core:2.2.3")
    implementation("io.ktor:ktor-client-cio:2.2.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.2.3")

    implementation("org.telegram:telegrambots:6.5.0")
    implementation("org.telegram:telegrambots-abilities:6.5.0")

    implementation("org.apache.logging.log4j:log4j-slf4j18-impl:2.18.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}


tasks {
    named<Test>("test") {
        useJUnitPlatform()
    }

    named<Jar>("jar") {
        manifest {
            attributes(
                "Main-Class" to "de.arisendrake.patreonrewardavailabilitybot.MainKt",
                "Multi-Release" to true
            )
        }
    }

    named<ShadowJar>("shadowJar"){
        mergeServiceFiles()
        archiveFileName.set("patreon-availability-bot.jar")
    }
}


application {
    mainClass.set("de.arisendrake.patreonrewardavailabilitybot.MainKt")
}
