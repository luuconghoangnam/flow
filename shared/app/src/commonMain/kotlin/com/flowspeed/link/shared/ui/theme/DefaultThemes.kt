package com.flowspeed.link.shared.ui.theme

import androidx.compose.ui.graphics.Color
import com.flowspeed.link.shared.util.ui.MyColors

object DefaultThemes {
    val dark = MyColors(
        id = "dark",
        name = "Cyber Dark",
        primary = Color(0xFFE64A00),
        primaryVariant = Color(0xFFFF6B35),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF3B82F6),
        secondaryVariant = Color(0xFF60A5FA),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFE5E7EB),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFE5E7EB),
        error = Color(0xFFEF4444),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF22C55E),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFEAB308),
        onWarning = Color(0xFF0A0A0A),
        info = Color(0xFF3B82F6),
        onInfo = Color(0xFFFFFFFF),
        isLight = false
    )

    val light = MyColors(
        id = "light",
        name = "Cyber Light",
        primary = Color(0xFFE64A00),
        primaryVariant = Color(0xFFCC3D00),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF3B82F6),
        secondaryVariant = Color(0xFF2563EB),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1E1E1E),
        surface = Color(0xFFF8F8F8),
        onSurface = Color(0xFF1E1E1E),
        error = Color(0xFFEF4444),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF22C55E),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFEAB308),
        onWarning = Color(0xFF1E1E1E),
        info = Color(0xFF3B82F6),
        onInfo = Color(0xFFFFFFFF),
        isLight = true
    )

    val obsidian = MyColors(
        id = "obsidian",
        name = "Terminal",
        primary = Color(0xFFE64A00),
        onPrimary = Color.White,
        secondary = Color(0xFF22C55E),
        onSecondary = Color.White,
        background = Color(0xFF0D0D0D),
        onBackground = Color(0xFFA0A0A0),
        onSurface = Color(0xFFA0A0A0),
        surface = Color(0xFF161616),
        error = Color(0xFFEF4444),
        onError = Color.White,
        success = Color(0xFF22C55E),
        onSuccess = Color.White,
        warning = Color(0xFFEAB308),
        onWarning = Color.White,
        info = Color(0xFF3B82F6),
        onInfo = Color.White,
        isLight = false,
    )

    val deepOcean = MyColors(
        id = "deep_ocean",
        name = "Deep Ocean",
        primary = Color(0xFFE64A00),
        primaryVariant = Color(0xFFFF6B35),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF3B82F6),
        secondaryVariant = Color(0xFF60A5FA),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFF0F1923),
        onBackground = Color(0xFFE5EAF2),
        surface = Color(0xFF1A2736),
        onSurface = Color(0xFFE5EAF2),
        error = Color(0xFFEF4444),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF22C55E),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFEAB308),
        onWarning = Color(0xFF1E1E1E),
        info = Color(0xFF3B82F6),
        onInfo = Color(0xFFFFFFFF),
        isLight = false
    )

    val black = MyColors(
        id = "black",
        name = "OLED Black",
        primary = Color(0xFFE64A00),
        primaryVariant = Color(0xFFFF6B35),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF3B82F6),
        secondaryVariant = Color(0xFF60A5FA),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE5E7EB),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFE5E7EB),
        error = Color(0xFFEF4444),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF22C55E),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFEAB308),
        onWarning = Color(0xFF000000),
        info = Color(0xFF3B82F6),
        onInfo = Color(0xFF000000),
        isLight = false
    )

    val lightGray = MyColors(
        id = "light_gray",
        name = "Industrial",
        primary = Color(0xFFE64A00),
        primaryVariant = Color(0xFFFF6B35),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF3B82F6),
        secondaryVariant = Color(0xFF60A5FA),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFF0F0F0),
        onBackground = Color(0xFF1E1E1E),
        surface = Color(0xFFE5E5E5),
        onSurface = Color(0xFF1E1E1E),
        error = Color(0xFFEF4444),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF22C55E),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFEAB308),
        onWarning = Color(0xFF1E1E1E),
        info = Color(0xFF3B82F6),
        onInfo = Color(0xFF1E1E1E),
        isLight = true
    )


    fun getAll(): List<MyColors> {
        return listOf(
            dark,
            light,
            obsidian,
            deepOcean,
            black,
            lightGray,
        )
    }

    fun getDefaultDark() = dark
    fun getDefaultLight() = light
}
