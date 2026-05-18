import com.flowspeed.link.updateapplier.UpdateInstaller
import com.flowspeed.lib.util.osfileutil.FileUtils
import java.io.File

class ApkInstaller(
    private val apkFile: File,
) : UpdateInstaller {
    override fun installUpdate() {
        FileUtils.openFile(apkFile)
    }
}
