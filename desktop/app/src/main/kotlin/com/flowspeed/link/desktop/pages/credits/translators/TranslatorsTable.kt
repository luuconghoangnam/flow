package com.flowspeed.link.desktop.pages.credits.translators

import com.flowspeed.link.shared.ui.widget.table.customtable.CellSize
import com.flowspeed.link.shared.ui.widget.table.customtable.SortableCell
import com.flowspeed.link.shared.ui.widget.table.customtable.TableCell
import androidx.compose.ui.unit.dp
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.pages.credits.translators.LanguageTranslationInfo
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource

sealed interface TranslatorsCells : TableCell<LanguageTranslationInfo> {
    data object LanguageName : TranslatorsCells,
        SortableCell<LanguageTranslationInfo> {
        override fun comparator(): Comparator<LanguageTranslationInfo> = compareBy { it.locale }
        override val id: String = "language"
        override val name: StringSource = Res.string.language.asStringSource()
        override val size: CellSize = CellSize.Resizeable(100.dp..1000.dp, 200.dp)
    }

    data object Translators : TranslatorsCells {
        override val id: String = "translators"
        override val name: StringSource = Res.string.translators.asStringSource()
        override val size: CellSize = CellSize.Resizeable(100.dp..1000.dp, 350.dp)
    }

    companion object {
        fun all() = listOf(
            LanguageName,
            Translators,
        )
    }
}
