import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    java
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "de.arisendrake"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")

    implementation("org.telegram:telegrambots:5.7.1")
    implementation("org.telegram:telegrambots-abilities:5.7.1")

    implementation("org.apache.logging.log4j:log4j-api:2.17.2")
    implementation("org.apache.logging.log4j:log4j-core:2.17.2")

    // https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-slf4j18-impl
    implementation("org.apache.logging.log4j:log4j-slf4j18-impl:2.17.2")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
    }

    named<ShadowJar>("shadowJar"){
        mergeServiceFiles()
        manifest {
            attributes("Main-Class" to "de.arisendrake.patreonrewardavailabilitybot.MainKt")
        }
        archiveFileName.set("notifications-bot.jar")
    }
}


application {
    mainClass.set("de.arisendrake.patreonrewardavailabilitybot.MainKt")
}