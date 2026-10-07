plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.0.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("io.papermc.paper:paper-api:${project.property("minecraft_version")}-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

kotlin {
    jvmToolchain(21)
}

val pluginVersion = project.version.toString()

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName.set("chronosmp-paper")
        archiveVersion.set(project.property("mod_version").toString())
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(project.property("minecraft_version") as String)
    }
}
