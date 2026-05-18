package com.flowspeed.link.shared.util.ui.icon

import com.flowspeed.link.resources.icons.FlowIcons
import com.flowspeed.link.resources.icons.*
import com.flowspeed.link.shared.util.ui.BaseMyColors
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.link.shared.util.ui.AppIconSource

object MyIcons : BaseMyColors() {
    override val appIcon: IconSource by lazy { AppIconSource() }

    override val settings = FlowIcons.Settings.asIconSource("settings")
    override val flag = FlowIcons.Flag.asIconSource("flag")
    override val fast = FlowIcons.Fast.asIconSource("fast")
    override val search = FlowIcons.Search.asIconSource("search")
    override val info = FlowIcons.Info.asIconSource("info")
    override val check = FlowIcons.Check.asIconSource("check")
    override val link = FlowIcons.AddLink.asIconSource("link")
    override val download = FlowIcons.DownSpeed.asIconSource("download")
    override val permission = FlowIcons.Permission.asIconSource("permission")

    override val windowMinimize = FlowIcons.WindowMinimize.asIconSource("windowMinimize")
    override val windowFloating = FlowIcons.WindowFloating.asIconSource("windowFloating")
    override val windowMaximize = FlowIcons.WindowMaximize.asIconSource("windowMaximize")
    override val windowClose = FlowIcons.WindowClose.asIconSource("windowClose")

    override val exit = FlowIcons.Exit.asIconSource("exit")
    override val edit = FlowIcons.Edit.asIconSource("edit")
    override val undo = FlowIcons.Undo.asIconSource("undo")

    override val openSource = FlowIcons.OpenSource.asIconSource("openSource")
    override val telegram = FlowIcons.Telegram.asIconSource("telegram", false)
    override val speaker = FlowIcons.Speaker.asIconSource("speaker")
    override val group = FlowIcons.Group.asIconSource("group")

    override val browserMozillaFirefox = FlowIcons.BrowserMozillaFirefox.asIconSource("browserMozillaFirefox", false)
    override val browserGoogleChrome = FlowIcons.BrowserGoogleChrome.asIconSource("browserGoogleChrome", false)
    override val browserMicrosoftEdge = FlowIcons.BrowserMicrosoftEdge.asIconSource("browserMicrosoftEdge", false)
    override val browserOpera = FlowIcons.BrowserOpera.asIconSource("browserOpera", false)

    override val next = FlowIcons.Next.asIconSource("next")
    override val back = FlowIcons.Back.asIconSource("back")
    override val up = FlowIcons.Up.asIconSource("up")
    override val down = FlowIcons.Down.asIconSource("down")

    override val activeCount = FlowIcons.List.asIconSource("activeCount")
    override val speed = FlowIcons.DownSpeed.asIconSource("speed")

    override val resume = FlowIcons.Resume.asIconSource("resume")
    override val pause = FlowIcons.Pause.asIconSource("pause")
    override val stop = FlowIcons.Stop.asIconSource("stop")

    override val queue = FlowIcons.Queue.asIconSource("queue")
    override val queueStart = FlowIcons.QueueStart.asIconSource("queueStart")
    override val queueStop = FlowIcons.QueueStop.asIconSource("queueStop")

    override val remove = FlowIcons.Delete.asIconSource("remove")
    override val clear = FlowIcons.Clear.asIconSource("clear")
    override val add = FlowIcons.Plus.asIconSource("add")
    override val minus = FlowIcons.Minus.asIconSource("add")
    override val paste = FlowIcons.Clipboard.asIconSource("paste")

    override val copy = FlowIcons.Copy.asIconSource("copy")
    override val refresh = FlowIcons.Refresh.asIconSource("refresh")
    override val editFolder = FlowIcons.Folder.asIconSource("editFolder")

    override val share = FlowIcons.Share.asIconSource("share")
    override val file = FlowIcons.File.asIconSource("file")
    override val folder = FlowIcons.Folder.asIconSource("folder")
    override val folderFinished = FlowIcons.FolderFinished.asIconSource("folderFinished")
    override val folderUnfinished = FlowIcons.FolderUnfinished.asIconSource("folderUnfinished")

    override val fileOpen = file
    override val folderOpen = folder
    override val pictureFile = FlowIcons.FilePicture.asIconSource("fileOpen")
    override val musicFile = FlowIcons.FileMusic.asIconSource("folderOpen")
    override val zipFile = FlowIcons.FileZip.asIconSource("pictureFile")
    override val videoFile = FlowIcons.FileVideo.asIconSource("musicFile")
    override val applicationFile = FlowIcons.FileApplication.asIconSource("zipFile")
    override val documentFile = FlowIcons.FileDocument.asIconSource("videoFile")
    override val otherFile = FlowIcons.FileUnknown.asIconSource("applicationFile")

    override val lock = FlowIcons.Lock.asIconSource("lock")
    override val question = FlowIcons.QuestionMark.asIconSource("question")

    override val grip = FlowIcons.Grip.asIconSource("grip")
    override val sortUp = FlowIcons.Sort123.asIconSource("sortUp")
    override val sortDown = FlowIcons.Sort321.asIconSource("sortDown")
    override val verticalDirection = FlowIcons.VerticalDirection.asIconSource("verticalDirection")

    override val browserIntegration = FlowIcons.Earth.asIconSource("browserIntegration")
    override val appearance = FlowIcons.Colors.asIconSource("appearance")
    override val downloadEngine = FlowIcons.DownSpeed.asIconSource("downloadEngine")
    override val network = FlowIcons.Network.asIconSource("network")
    override val language = FlowIcons.Language.asIconSource("language")

    override val externalLink = FlowIcons.ExternalLink.asIconSource("externalLink")
    override val earth = FlowIcons.Earth.asIconSource("earth")
    override val hearth = FlowIcons.Hearth.asIconSource("hearth")
    override val dragAndDrop = FlowIcons.DragAndDrop.asIconSource("dragAndDrop")


    override val selectAll = FlowIcons.SelectAll.asIconSource("selectAll")
    override val selectInside = FlowIcons.SelectInside.asIconSource("selectInside")
    override val selectInvert = FlowIcons.SelectInvert.asIconSource("selectInvert")

    override val menu = FlowIcons.Menu.asIconSource("menu")

    override val close: IconSource = FlowIcons.Clear.asIconSource("close")

    override val data: IconSource = FlowIcons.Data.asIconSource("alphabet")
    override val alphabet: IconSource = FlowIcons.Alphabet.asIconSource("alphabet")
    override val clock: IconSource = FlowIcons.Clock.asIconSource("clock")
}
