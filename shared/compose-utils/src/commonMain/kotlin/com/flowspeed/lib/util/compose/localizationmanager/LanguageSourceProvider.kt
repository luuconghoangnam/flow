package com.flowspeed.lib.util.compose.localizationmanager

import com.flowspeed.lib.resources.contracts.MyLanguageResource

/**
 * at the moment we only use bundled strings
 */
class LanguageSourceProvider(
    val defaultLanguageResource: MyLanguageResource,
    val allLanguageResources: List<MyLanguageResource>,
)
