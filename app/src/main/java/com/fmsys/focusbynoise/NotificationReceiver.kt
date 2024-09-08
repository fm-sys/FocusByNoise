package com.fmsys.focusbynoise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fmsys.focusbynoise.helpers.*

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val actions = arrayOf(PLAY, PAUSE, TOGGLE_PLAY, DISMISS)
        if (!actions.contains(action) || action == null) {
            return
        }
        PlayerService.start(context, action)
    }
}
