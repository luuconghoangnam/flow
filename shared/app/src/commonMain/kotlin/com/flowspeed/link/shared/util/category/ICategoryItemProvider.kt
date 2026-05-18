package com.flowspeed.link.shared.util.category

interface ICategoryItemProvider {
    suspend fun getAll(): List<CategoryItemWithId>
}
