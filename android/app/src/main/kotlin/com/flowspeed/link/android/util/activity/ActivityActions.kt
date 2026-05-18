package com.flowspeed.link.android.util.activity

import android.content.Intent
import com.flowspeed.link.shared.util.mvi.ContainsEffects

interface ActivityActions {
    fun startActivityAction(intent: Intent)
    fun finishActivityAction()
}
