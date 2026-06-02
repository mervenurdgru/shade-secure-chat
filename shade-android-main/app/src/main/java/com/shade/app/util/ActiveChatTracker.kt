package com.shade.app.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveChatTracker @Inject constructor() {

    @Volatile
    var activeShadeId: String? = null
        private set

    fun setActive(shadeId: String) {
        activeShadeId = shadeId
    }

    fun clear() {
        activeShadeId = null
    }
}