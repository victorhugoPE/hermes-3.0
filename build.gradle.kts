import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "br.com.hermes"
version = "3.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.xerial:sqlite-jdbc:3.49.0.0")
    implementation("org.slf4j:slf4j-nop:2.0.16")

    // Ktor para chamadas HTTP e streaming SSE com LLMs
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

sourceSets {
    main {
        kotlin.srcDirs("src")
        java.srcDirs("src")
        resources.srcDirs("resources")
    }
    test {
        kotlin.srcDirs("test")
    }
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("hermes")
    archiveClassifier.set("")
    archiveVersion.set("3.0.0")
    destinationDirectory.set(file("dist"))
    manifest {
        attributes(
            "Main-Class" to "br.com.hermes.MainKt",
            "Implementation-Version" to project.version.toString()
        )
    }
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
