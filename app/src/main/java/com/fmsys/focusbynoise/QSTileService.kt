package com.fmsys.focusbynoise

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.fmsys.focusbynoise.helpers.*

@RequiresApi(Build.VERSION_CODES.N)
class QSTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if(qsTile.state == Tile.STATE_INACTIVE) {
            PlayerService.start(applicationContext, PLAY)
        } else {
            PlayerService.start(applicationContext, PAUSE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        requestListeningState(this,
            ComponentName(this, QSTileService::class.java)
        )
        return super.onBind(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    fun updateTile() {
        val isPlaying = getSharedPreferences(applicationContext.packageName, 0)
            .getBoolean("isPlaying", false)
        val resource = if (isPlaying) R.drawable.notification_icon else R.drawable.paused_notification_icon
        qsTile.setIcon(Icon.createWithResource(this, resource))
        qsTile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val desc = if (isPlaying) getResources().getString(R.string.playing) else getResources().getString(R.string.paused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            qsTile.setStateDescription(desc)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.setSubtitle(desc)
        }
        qsTile.updateTile()
    }
}
