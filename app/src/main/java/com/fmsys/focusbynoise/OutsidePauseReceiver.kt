package com.fmsys.focusbynoise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.fmsys.focusbynoise.helpers.AUDIO_BECOMING_NOISY
import com.fmsys.focusbynoise.helpers.CONNECTION_STATE_CHANGED
import com.fmsys.focusbynoise.helpers.HEADPHONES_CONNECTED
import com.fmsys.focusbynoise.helpers.HEADSET_PLUG
import com.fmsys.focusbynoise.helpers.HEADSET_STATE_CHANGED

class OutsidePauseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val playerAction = when (intent.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // Headphones unplugged / disconnected
                AUDIO_BECOMING_NOISY
            }
            HEADSET_PLUG -> {
                // Wired headset monitoring
                val state = intent.getIntExtra("state", 0)
                if (state > 0) HEADPHONES_CONNECTED
                else null
            }
            HEADSET_STATE_CHANGED -> {
                // Bluetooth monitoring
                val state = intent.getIntExtra("android.bluetooth.headset.extra.STATE", 0)
                if (state == 2) HEADPHONES_CONNECTED
                else null
            }
            CONNECTION_STATE_CHANGED -> {
                // Bluetooth, works for Ice Cream Sandwich
                val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0)
                if (state == 2) HEADPHONES_CONNECTED
                else null
            }
            else -> null
        }

        if (playerAction != null) {
            PlayerService.start(context, playerAction)
        }

    }
}
