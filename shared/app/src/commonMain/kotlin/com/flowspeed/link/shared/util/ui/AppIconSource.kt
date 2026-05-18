package com.flowspeed.link.shared.util.ui

import com.flowspeed.lib.util.compose.IconSource

/**
 * Creates an IconSource for the app icon.
 * On Desktop: loads PNG from resources.
 * On Android: uses the vector fallback.
 */
expect fun AppIconSource(): IconSource
