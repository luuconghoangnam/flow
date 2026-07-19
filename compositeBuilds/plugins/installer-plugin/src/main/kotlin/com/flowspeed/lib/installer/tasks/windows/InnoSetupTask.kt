package com.flowspeed.lib.installer.tasks.windows

import com.flowspeed.lib.installer.extensiion.WindowsConfig
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class InnoSetupTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @get:InputDirectory
    abstract val sourceFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val destFolder: DirectoryProperty

    @get:Input
    abstract val outputFileName: Property<String>

    @get:InputFile
    abstract val innoTemplate: Property<File>

    @get:Input
    abstract val commonParams: Property<WindowsConfig>

    @get:Input
    val extraParams: MapProperty<String, Any> = project.objects.mapProperty<String, Any>()

    @get:Internal
    abstract val innoExecutable: Property<File>

    init {
        innoExecutable.convention(
            project.provider {
                listOf(
                    File("C:\\Program Files\\Inno Setup 7\\ISCC.exe"),
                    File("C:\\Program Files (x86)\\Inno Setup 7\\ISCC.exe"),
                    File("C:\\Program Files\\Inno Setup 6\\ISCC.exe"),
                    File("C:\\Program Files (x86)\\Inno Setup 6\\ISCC.exe"),
                ).firstOrNull { it.isFile } ?: File("ISCC.exe")
            }
        )
    }

    private fun createHandleBarContext(): Context {
        val commonParams = commonParams.get()
        val appExe = "{app}\\${commonParams.appName!!}.exe"
        val common = mapOf(
            "app_name" to commonParams.appName!!,
            "app_display_name" to commonParams.appDisplayName!!,
            "app_version" to commonParams.appVersion!!,
            "app_display_version" to commonParams.appDisplayVersion!!,
            "app_data_dir_name" to commonParams.appDataDirName!!,
            "app_install_dir" to "{autopf}\\${commonParams.appDisplayName!!}",
            "app_exe" to appExe,
            "app_group_icon" to "{group}\\${commonParams.appDisplayName!!}",
            "app_desktop_icon" to "{autodesktop}\\${commonParams.appDisplayName!!}",
            "app_startup_icon" to "{userstartup}\\${commonParams.appDisplayName!!}",
            "app_data_dir" to "{userappdata}\\${commonParams.appDataDirName!!}",
            "license_file" to commonParams.licenceFile!!.absolutePath,
            "icon_file" to commonParams.iconFile!!.absolutePath,
            "output_dir" to destFolder.get().asFile.absolutePath,
            "output_base_filename" to outputFileName.get(),
            "input_dir" to sourceFolder.get().asFile.absolutePath,
        )
        return Context.newContext(
            extraParams
                .get()
                .plus(common)
        )
    }

    @TaskAction
    fun run() {
        val executable = innoExecutable.get()
        val template = innoTemplate.get()
        val handlebars = Handlebars()
        val script = handlebars.compileInline(template.readText()).apply(createHandleBarContext())
        val generatedScript = temporaryDir.resolve("${outputFileName.get()}.iss")

        destFolder.get().asFile.mkdirs()
        generatedScript.parentFile.mkdirs()
        generatedScript.writeText(script)

        logger.lifecycle("Inno Setup script written to ${generatedScript.absolutePath}")
        execOps.exec {
            executable(executable)
            args(generatedScript.absolutePath)
        }
    }
}
