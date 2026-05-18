"""Regenerate icons with proper Kotlin path DSL formatting."""
import os, re

ICONS_DIR = "d:/Repos/IDM/shared/resources/src/commonMain/kotlin/com/flowspeed/link/resources/icons"

# Icon paths - each command on its own line
ICONS = {
    "Settings": [
        "moveTo(3f, 17f)", "lineTo(9f, 17f)", "lineTo(9f, 15f)", "lineTo(3f, 15f)", "close()",
        "moveTo(3f, 5f)", "lineTo(3f, 7f)", "lineTo(13f, 7f)", "lineTo(13f, 5f)", "close()",
        "moveTo(13f, 21f)", "lineTo(13f, 19f)", "lineTo(21f, 19f)", "lineTo(21f, 17f)", "lineTo(13f, 17f)", "lineTo(13f, 15f)", "lineTo(11f, 15f)", "lineTo(11f, 21f)", "close()",
        "moveTo(7f, 11f)", "lineTo(7f, 9f)", "lineTo(3f, 9f)", "lineTo(3f, 11f)", "close()",
        "moveTo(21f, 11f)", "lineTo(21f, 9f)", "lineTo(9f, 9f)", "lineTo(9f, 11f)", "close()",
        "moveTo(15f, 5f)", "lineTo(15f, 7f)", "lineTo(21f, 7f)", "lineTo(21f, 5f)", "close()",
        "moveTo(17f, 11f)", "lineTo(17f, 13f)", "lineTo(21f, 13f)", "lineTo(21f, 11f)", "close()",
    ],
    "Search": [
        "moveTo(15.5f, 14f)", "lineTo(14.7f, 14f)", "lineTo(14.4f, 14.3f)",
        "curveTo(13.3f, 15.2f, 11.8f, 15.7f, 10.2f, 15.7f)",
        "curveTo(6.7f, 15.7f, 3.8f, 12.8f, 3.8f, 9.3f)",
        "curveTo(3.8f, 5.8f, 6.7f, 2.9f, 10.2f, 2.9f)",
        "curveTo(13.7f, 2.9f, 16.6f, 5.8f, 16.6f, 9.3f)",
        "curveTo(16.6f, 10.9f, 16.1f, 12.4f, 15.2f, 13.5f)",
        "lineTo(14.9f, 13.8f)", "lineTo(14.9f, 14.6f)", "lineTo(20.4f, 20.1f)", "lineTo(19.2f, 21.3f)", "close()",
        "moveTo(10.2f, 5f)",
        "curveTo(7.9f, 5f, 5.9f, 7f, 5.9f, 9.3f)",
        "curveTo(5.9f, 11.6f, 7.9f, 13.6f, 10.2f, 13.6f)",
        "curveTo(12.5f, 13.6f, 14.5f, 11.6f, 14.5f, 9.3f)",
        "curveTo(14.5f, 7f, 12.5f, 5f, 10.2f, 5f)", "close()",
    ],
    "Resume": [
        "moveTo(6f, 4f)", "lineTo(6f, 20f)", "lineTo(20f, 12f)", "close()",
    ],
    "Pause": [
        "moveTo(6f, 4f)", "lineTo(6f, 20f)", "lineTo(10f, 20f)", "lineTo(10f, 4f)", "close()",
        "moveTo(14f, 4f)", "lineTo(14f, 20f)", "lineTo(18f, 20f)", "lineTo(18f, 4f)", "close()",
    ],
    "Stop": [
        "moveTo(4f, 4f)", "lineTo(4f, 20f)", "lineTo(20f, 20f)", "lineTo(20f, 4f)", "close()",
    ],
    "Delete": [
        "moveTo(6f, 7f)", "lineTo(6f, 21f)", "lineTo(18f, 21f)", "lineTo(18f, 7f)", "close()",
        "moveTo(4f, 5f)", "lineTo(20f, 5f)", "lineTo(20f, 7f)", "lineTo(4f, 7f)", "close()",
        "moveTo(9f, 3f)", "lineTo(15f, 3f)", "lineTo(15f, 5f)", "lineTo(9f, 5f)", "close()",
    ],
    "Plus": [
        "moveTo(11f, 5f)", "lineTo(11f, 11f)", "lineTo(5f, 11f)", "lineTo(5f, 13f)",
        "lineTo(11f, 13f)", "lineTo(11f, 19f)", "lineTo(13f, 19f)", "lineTo(13f, 13f)",
        "lineTo(19f, 13f)", "lineTo(19f, 11f)", "lineTo(13f, 11f)", "lineTo(13f, 5f)", "close()",
    ],
    "Minus": [
        "moveTo(5f, 11f)", "lineTo(19f, 11f)", "lineTo(19f, 13f)", "lineTo(5f, 13f)", "close()",
    ],
    "Check": [
        "moveTo(9f, 16.2f)", "lineTo(4.8f, 12f)", "lineTo(3.4f, 13.4f)",
        "lineTo(9f, 19f)", "lineTo(21f, 7f)", "lineTo(19.6f, 5.6f)", "close()",
    ],
    "Clear": [
        "moveTo(19f, 6.4f)", "lineTo(17.6f, 5f)", "lineTo(12f, 10.6f)",
        "lineTo(6.4f, 5f)", "lineTo(5f, 6.4f)", "lineTo(10.6f, 12f)",
        "lineTo(5f, 17.6f)", "lineTo(6.4f, 19f)", "lineTo(12f, 13.4f)",
        "lineTo(17.6f, 19f)", "lineTo(19f, 17.6f)", "lineTo(13.4f, 12f)", "close()",
    ],
    "Copy": [
        "moveTo(4f, 2f)", "lineTo(4f, 16f)", "lineTo(14f, 16f)", "lineTo(14f, 2f)", "close()",
        "moveTo(6f, 4f)", "lineTo(12f, 4f)", "lineTo(12f, 14f)", "lineTo(6f, 14f)", "close()",
        "moveTo(8f, 18f)", "lineTo(20f, 18f)", "lineTo(20f, 6f)", "lineTo(16f, 6f)",
        "lineTo(16f, 16f)", "lineTo(8f, 16f)", "close()",
    ],
    "Edit": [
        "moveTo(3f, 17.3f)", "lineTo(3f, 21f)", "lineTo(6.7f, 21f)",
        "lineTo(17.8f, 9.9f)", "lineTo(14.1f, 6.2f)", "close()",
        "moveTo(20.7f, 7f)", "lineTo(17f, 3.3f)", "lineTo(15.2f, 5.1f)",
        "lineTo(18.9f, 8.8f)", "close()",
    ],
    "Refresh": [
        "moveTo(17.6f, 6.4f)",
        "curveTo(16.2f, 5f, 14.2f, 4f, 12f, 4f)",
        "curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)",
        "curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)",
        "curveTo(15.7f, 20f, 18.8f, 17.4f, 19.7f, 14f)",
        "lineTo(17.6f, 14f)",
        "curveTo(16.8f, 16.3f, 14.6f, 18f, 12f, 18f)",
        "curveTo(8.7f, 18f, 6f, 15.3f, 6f, 12f)",
        "curveTo(6f, 8.7f, 8.7f, 6f, 12f, 6f)",
        "curveTo(13.7f, 6f, 15.1f, 6.7f, 16.2f, 7.8f)",
        "lineTo(13f, 11f)", "lineTo(20f, 11f)", "lineTo(20f, 4f)", "close()",
    ],
    "Folder": [
        "moveTo(2f, 6f)", "lineTo(2f, 18f)", "lineTo(22f, 18f)",
        "lineTo(22f, 8f)", "lineTo(12f, 8f)", "lineTo(10f, 6f)", "close()",
    ],
    "File": [
        "moveTo(4f, 2f)", "lineTo(4f, 22f)", "lineTo(20f, 22f)",
        "lineTo(20f, 8f)", "lineTo(14f, 2f)", "close()",
        "moveTo(14f, 2f)", "lineTo(14f, 8f)", "lineTo(20f, 8f)", "close()",
    ],
    "Info": [
        "moveTo(11f, 7f)", "lineTo(13f, 7f)", "lineTo(13f, 9f)", "lineTo(11f, 9f)", "close()",
        "moveTo(11f, 11f)", "lineTo(13f, 11f)", "lineTo(13f, 17f)", "lineTo(11f, 17f)", "close()",
        "moveTo(4f, 4f)", "lineTo(4f, 20f)", "lineTo(20f, 20f)", "lineTo(20f, 4f)", "close()",
        "moveTo(6f, 6f)", "lineTo(18f, 6f)", "lineTo(18f, 18f)", "lineTo(6f, 18f)", "close()",
    ],
    "Exit": [
        "moveTo(5f, 5f)", "lineTo(5f, 19f)", "lineTo(13f, 19f)", "lineTo(13f, 17f)",
        "lineTo(7f, 17f)", "lineTo(7f, 7f)", "lineTo(13f, 7f)", "lineTo(13f, 5f)", "close()",
        "moveTo(16f, 8f)", "lineTo(14.6f, 9.4f)", "lineTo(16.2f, 11f)", "lineTo(9f, 11f)",
        "lineTo(9f, 13f)", "lineTo(16.2f, 13f)", "lineTo(14.6f, 14.6f)",
        "lineTo(16f, 16f)", "lineTo(20f, 12f)", "close()",
    ],
    "Back": [
        "moveTo(20f, 11f)", "lineTo(7.8f, 11f)", "lineTo(13.4f, 5.4f)",
        "lineTo(12f, 4f)", "lineTo(4f, 12f)", "lineTo(12f, 20f)",
        "lineTo(13.4f, 18.6f)", "lineTo(7.8f, 13f)", "lineTo(20f, 13f)", "close()",
    ],
    "Next": [
        "moveTo(4f, 13f)", "lineTo(16.2f, 13f)", "lineTo(10.6f, 18.6f)",
        "lineTo(12f, 20f)", "lineTo(20f, 12f)", "lineTo(12f, 4f)",
        "lineTo(10.6f, 5.4f)", "lineTo(16.2f, 11f)", "lineTo(4f, 11f)", "close()",
    ],
    "Up": [
        "moveTo(12f, 4f)", "lineTo(4f, 12f)", "lineTo(5.4f, 13.4f)",
        "lineTo(11f, 7.8f)", "lineTo(11f, 20f)", "lineTo(13f, 20f)",
        "lineTo(13f, 7.8f)", "lineTo(18.6f, 13.4f)", "lineTo(20f, 12f)", "close()",
    ],
    "Down": [
        "moveTo(12f, 20f)", "lineTo(20f, 12f)", "lineTo(18.6f, 10.6f)",
        "lineTo(13f, 16.2f)", "lineTo(13f, 4f)", "lineTo(11f, 4f)",
        "lineTo(11f, 16.2f)", "lineTo(5.4f, 10.6f)", "lineTo(4f, 12f)", "close()",
    ],
    "DownSpeed": [
        "moveTo(11f, 3f)", "lineTo(13f, 3f)", "lineTo(13f, 13.2f)",
        "lineTo(16.6f, 9.6f)", "lineTo(18f, 11f)", "lineTo(12f, 17f)",
        "lineTo(6f, 11f)", "lineTo(7.4f, 9.6f)", "lineTo(11f, 13.2f)", "close()",
        "moveTo(5f, 19f)", "lineTo(19f, 19f)", "lineTo(19f, 21f)", "lineTo(5f, 21f)", "close()",
    ],
    "Lock": [
        "moveTo(6f, 10f)", "lineTo(6f, 20f)", "lineTo(18f, 20f)", "lineTo(18f, 10f)", "close()",
        "moveTo(8f, 10f)", "lineTo(8f, 7f)",
        "curveTo(8f, 4.8f, 9.8f, 3f, 12f, 3f)",
        "curveTo(14.2f, 3f, 16f, 4.8f, 16f, 7f)",
        "lineTo(16f, 10f)", "lineTo(14f, 10f)", "lineTo(14f, 7f)",
        "curveTo(14f, 5.9f, 13.1f, 5f, 12f, 5f)",
        "curveTo(10.9f, 5f, 10f, 5.9f, 10f, 7f)",
        "lineTo(10f, 10f)", "close()",
    ],
    "Menu": [
        "moveTo(3f, 6f)", "lineTo(21f, 6f)", "lineTo(21f, 8f)", "lineTo(3f, 8f)", "close()",
        "moveTo(3f, 11f)", "lineTo(21f, 11f)", "lineTo(21f, 13f)", "lineTo(3f, 13f)", "close()",
        "moveTo(3f, 16f)", "lineTo(21f, 16f)", "lineTo(21f, 18f)", "lineTo(3f, 18f)", "close()",
    ],
    "Share": [
        "moveTo(18f, 2f)", "lineTo(22f, 6f)", "lineTo(18f, 10f)", "lineTo(18f, 7f)",
        "lineTo(10f, 7f)", "lineTo(10f, 13f)", "lineTo(8f, 13f)", "lineTo(8f, 5f)",
        "lineTo(18f, 5f)", "close()",
        "moveTo(4f, 9f)", "lineTo(6f, 9f)", "lineTo(6f, 19f)", "lineTo(18f, 19f)",
        "lineTo(18f, 13f)", "lineTo(20f, 13f)", "lineTo(20f, 21f)", "lineTo(4f, 21f)", "close()",
    ],
}

TEMPLATE = '''package com.flowspeed.link.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FlowIcons.{name}: ImageVector
    get() {{
        if (_{name} != null) {{
            return _{name}!!
        }}
        _{name} = ImageVector.Builder(
            name = "{name}",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {{
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd
            ) {{
{paths}
            }}
        }}.build()

        return _{name}!!
    }}

@Suppress("ObjectPropertyName")
private var _{name}: ImageVector? = null
'''

def main():
    count = 0
    for name, cmds in ICONS.items():
        filepath = os.path.join(ICONS_DIR, f"{name}.kt")
        if os.path.exists(filepath):
            paths = "\n".join(f"                {cmd}" for cmd in cmds)
            content = TEMPLATE.format(name=name, paths=paths)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            count += 1
    print(f"Replaced {count} icons")

if __name__ == "__main__":
    main()
