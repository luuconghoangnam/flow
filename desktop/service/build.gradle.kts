/**
 * Flow Download Manager - Background Service
 *
 * Lightweight native service that handles downloads, queue management,
 * and browser extension communication without requiring a UI.
 * Compiled with GraalVM native-image for minimal memory footprint (~20-30MB).
 */
plugins {
    id(MyPlugins.kotlin)
    id(Plugins.Kotlin.serialization)
}

dependencies {
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.okio.okio)
    implementation(libs.nanoHttpd.core)

    implementation(project(":downloader:core"))
    implementation(project(":downloader:monitor"))
    implementation(project(":integration:server"))
    implementation(project(":shared:app"))
    implementation(project(":shared:utils"))
    implementation(project(":shared:local-server"))
    implementation(project(":shared:config"))
}

tasks.register<JavaExec>("run") {
    mainClass.set("com.flowspeed.link.service.ServiceMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs(
        "-Xms8m",
        "-Xmx64m",
        "-XX:+UseG1GC",
        "-XX:MaxHeapFreeRatio=20",
        "-XX:MinHeapFreeRatio=5",
    )
}
