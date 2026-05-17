plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
}
version = 1
group = "com.flowspeed.lib.plugin"
dependencies {
    implementation(libs.semver)
    implementation(libs.jgit)
}
gradlePlugin {
    plugins {
        create("git-version-plugin") {
            id = "com.flowspeed.lib.git-version-plugin"
            implementationClass = "com.flowspeed.lib.git_version.GitVersionPlugin"
        }
    }
}