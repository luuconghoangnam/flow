package com.flowspeed.link.desktop.pages.category

import com.flowspeed.link.shared.pagemanager.CategoryDialogManager
import com.flowspeed.link.shared.pages.category.CategoryComponent
import kotlinx.coroutines.flow.StateFlow

interface DesktopCategoryDialogManager : CategoryDialogManager {
    val openedCategoryDialogs: StateFlow<List<CategoryComponent>>
    fun closeCategoryDialog(categoryId: Long)
}
