plugins {
    kotlin("jvm")
    id("fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")

    implementation(project(":core"))
    include(implementation("org.yaml:snakeyaml:2.4")!!)
    include(modImplementation("me.lucko:fabric-permissions-api:0.6.1")!!)
}

val modVersion = project.version.toString()

tasks {
    processResources {
        inputs.property("version", modVersion)
        filesMatching("fabric.mod.json") {
            expand("version" to modVersion)
        }
    }

    jar {
        archiveBaseName.set("chronosmp-fabric-${project.property("minecraft_version")}")
        archiveVersion.set(project.property("mod_version").toString())
        from("LICENSE") {
            rename { "${it}_${project.property("archives_base_name")}" }
        }
    }

    remapJar {
        archiveBaseName.set("chronosmp-fabric-${project.property("minecraft_version")}")
        archiveVersion.set(project.property("mod_version").toString())
    }
}
