plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.yaml:snakeyaml:2.4")
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}
