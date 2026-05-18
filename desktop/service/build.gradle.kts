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
    application
    id("org.graalvm.buildtools.native") version "0.10.6"
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

application {
    mainClass.set("com.flowspeed.link.service.ServiceMainKt")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("flow-service")
            mainClass.set("com.flowspeed.link.service.ServiceMainKt")
            buildArgs.addAll(
                "--no-fallback",
                "--enable-url-protocols=http,https",
                "-H:+ReportExceptionStackTraces",
                "--gc=serial",
                "-O2",
            )
        }
    }
}

// Run task is provided by the application plugin
// Use: ./gradlew :desktop:service:run --args="--port 15151"
