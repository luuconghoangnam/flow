package com.flowspeed.lib.git_version.core


fun interface TagSelector {
    fun select(tags: List<GitReference.TagInfo>): GitReference.TagInfo?
}
