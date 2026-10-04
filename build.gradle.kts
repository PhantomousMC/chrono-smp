plugins {
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.serialization") version "2.3.10" apply false
    id("fabric-loom") version "1.14-SNAPSHOT" apply false
}

allprojects {
    group = project.property("maven_group") as String
    version = project.property("mod_version") as String
}

subprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
        maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    plugins.withType<org.gradle.api.plugins.JavaPlugin>().configureEach {
        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}
