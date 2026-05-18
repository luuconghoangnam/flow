/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.shared.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowspeed.link.shared.util.category.Category
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.resources.myStringResource

/**
 * Horizontal category navigation bar - replaces the left sidebar.
 * Cyber-Industrial style: sharp edges, orange accent indicator.
 */
@Composable
fun CyberNavigationBar(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(myColors.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "All" tab
            CyberTab(
                label = myStringResource(Res.string.all),
                isSelected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
            )

        // Category tabs
        categories.forEach { category ->
            CyberTab(
                label = category.name,
                isSelected = selectedCategory?.id == category.id,
                onClick = { onCategorySelected(category) },
            )
        }
        }
        // Animated accent line below nav
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            myColors.primary.copy(alpha = 0.4f),
                            myColors.primary,
                            myColors.primary.copy(alpha = 0.4f),
                            Color.Transparent,
                        )
                    )
                )
        )
    }
}

@Composable
private fun CyberTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = myTextSizes.sm,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) myColors.primary else myColors.onSurface / 0.7f,
        )
        Spacer(Modifier.height(4.dp))
        // Active indicator bar
        Box(
            Modifier
                .width(32.dp)
                .height(2.dp)
                .background(
                    if (isSelected) myColors.primary else myColors.surface,
                    RectangleShape
                )
        )
    }
}
