import buildlogic.versioning.getAppDataDirName
import buildlogic.versioning.getAppName
import buildlogic.versioning.getAppVersionString
import buildlogic.versioning.getApplicationPackageName
import buildlogic.versioning.getPrettifiedAppName
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(MyPlugins.kotlinMultiplatform)
    id(MyPlugins.composeBase)
    id(Plugins.Kotlin.serialization)
    id(Plugins.Android.library)
    id(Plugins.buildConfig)
}
kotlin {
    jvm("desktop")
    androidTarget("android") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)

            api(project(":downloader:core"))
            api(project(":downloader:monitor"))

            api(project(":shared:config"))
            api(project(":shared:utils"))
            api(project(":shared:compose-utils"))
            api(project(":shared:resources"))
            api(project(":shared:auto-start"))
            api(project(":shared:updater"))

            api(libs.kotlin.coroutines.core)
            api(libs.kotlin.serialization.json)

            api(libs.decompose)
            api(libs.essenty.lifecycleCoroutines)
            api(libs.koin.core)

            api(libs.androidx.datastore)

            implementation(libs.kotlinFileWatcher)

            //because we don't have material design, but we use ripple effect
            implementation(libs.compose.material.rippleEffect)

            // multiplatform scrollbars
            api(libs.fastscroller.core)
            api(libs.markdownRenderer.core)
            api(libs.compose.reorderable)
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.androidx.activity.compose)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.osThemeDetector.get().toString()) {
                exclude(group = "net.java.dev.jna")
            }
        }
    }
}

android {
    compileSdk = 36
    namespace = "com.flowspeed.link.shared"
    defaultConfig {
        minSdk = 26
    }
}
// generate a file with these constants
buildConfig {
    packageName = "com.flowspeed.link.shared"
    buildConfigField(
        "PACKAGE_NAME",
        provider {
            getApplicationPackageName()
        }
    )
    buildConfigField(
        "APP_DISPLAY_NAME",
        provider { getPrettifiedAppName() }
    )
    buildConfigField(
        "DATA_DIR_NAME",
        provider { getAppDataDirName() }
    )
    buildConfigField(
        "APP_VERSION",
        provider { getAppVersionString() }
    )
    buildConfigField(
        "APP_NAME",
        provider { getAppName() }
    )
    buildConfigField(
        "PROJECT_WEBSITE",
        provider {
            "https://flowspeed.link"
        }
    )
    buildConfigField(
        "PROJECT_SOURCE_CODE",
        provider {
            "https://github.com/flowspeed/flow-download-manager"
        }
    )
    buildConfigField(
        "DONATE_LINK",
        provider {
            ""
        }
    )
    buildConfigField(
        "PROJECT_GITHUB_OWNER",
        provider {
            "flowspeed"
        }
    )
    buildConfigField(
        "PROJECT_GITHUB_REPO",
        provider {
            "flow-download-manager"
        }
    )
    buildConfigField(
        "PROJECT_TRANSLATIONS",
        provider {
            ""
        }
    )
    buildConfigField(
        "INTEGRATION_CHROME_LINK",
        provider {
            ""
        }
    )
    buildConfigField(
        "INTEGRATION_FIREFOX_LINK",
        provider {
            ""
        }
    )
    buildConfigField(
        "TELEGRAM_GROUP",
        provider {
            ""
        }
    )
    buildConfigField(
        "TELEGRAM_CHANNEL",
        provider {
            ""
        }
    )
}
