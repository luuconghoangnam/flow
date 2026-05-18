package com.flowspeed.link.shared.util

import androidx.compose.runtime.Immutable
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.link.shared.util.ui.icon.MyIcons

sealed class BrowserType(
    val code:String
){
    data object Firefox : BrowserType("firefox")
    data object Chrome : BrowserType("chrome")
    data object Opera : BrowserType("opera")
    data object Edge : BrowserType("edge")
}
fun BrowserType.getName():String{
    return when(this){
        BrowserType.Chrome -> "Google Chrome"
        BrowserType.Edge -> "Microsoft Edge"
        BrowserType.Firefox -> "Mozilla Firefox"
        BrowserType.Opera -> "Opera"
    }
}
fun BrowserType.getIcon(): IconSource {
    return when(this){
        BrowserType.Chrome -> MyIcons.browserGoogleChrome
        BrowserType.Edge -> MyIcons.browserMicrosoftEdge
        BrowserType.Firefox -> MyIcons.browserMozillaFirefox
        BrowserType.Opera -> MyIcons.browserOpera
    }
}
@Immutable
data class BrowserIntegrationModel(
    val type: BrowserType,
    val url:String,
)
