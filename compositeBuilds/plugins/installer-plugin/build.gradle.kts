plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
}
version = 1
group = "com.flowspeed.lib.plugin"
dependencies {
    implementation("com.flowspeed.lib.util:platform:1")
    implementation(libs.handlebarsJava)
}
gradlePlugin {
    plugins {
        create("installer-plugin") {
            id = "com.flowspeed.lib.installer-plugin"
            implementationClass = "com.flowspeed.lib.installer.InstallerPlugin"
        }
    }
}
