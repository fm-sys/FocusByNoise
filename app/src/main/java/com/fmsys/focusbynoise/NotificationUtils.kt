package com.fmsys.focusbynoise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.MediaMetadata
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.fmsys.focusbynoise.helpers.*

class NotificationUtils(base: Context?) : ContextWrapper(base) {

    private var mManager: NotificationManager? = null
    val mediaSession = MediaSessionCompat(this, "PlayerService")
    val mediaStyle = MediaStyle().setMediaSession(mediaSession.sessionToken)

    companion object {
        var NOTIFICATION_ID = 64 // Random number
    }

    private fun getManager(): NotificationManager? {
        if (mManager == null) {
            mManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        }
        return mManager
    }

    fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                enableLights(false)
                enableVibration(false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(false)
            }
            // Register the channel with the system
            getManager()?.createNotificationChannel(channel)
        }
    }

    fun createNotification(isPlaying: Boolean): Notification {

        createNotificationChannel()

//        val volUpIntent = Intent(this, NotificationReceiver::class.java).apply {
//            action = VOLUME_UP
//        }
//        val pendingVolUpIntent: PendingIntent =
//            PendingIntent.getBroadcast(this, 0, volUpIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        val volDownIntent = Intent(this, NotificationReceiver::class.java).apply {
//            action = VOLUME_DOWN
//        }
//        val pendingVolDownIntent: PendingIntent =
//            PendingIntent.getBroadcast(this, 0, volDownIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        val dismissIntent = Intent(this, NotificationReceiver::class.java).apply {
//            action = DISMISS
//        }
//        val pendingDismissIntent: PendingIntent =
//            PendingIntent.getBroadcast(this, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        val notificationLayout = RemoteViews(packageName, R.layout.notification_layout)

        val pauseIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, NotificationReceiver::class.java).apply {
                action = PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, NotificationReceiver::class.java).apply {
                action = PLAY
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingMainIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)


        if (isPlaying) {
            mediaSession.isActive = true
        }

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Focus by Noise")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, -1)
                .build()
        )


        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .build()
        )

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                PlayerService.start(applicationContext, PLAY)
            }

            override fun onPause() {
                super.onPause()
                PlayerService.start(applicationContext, PAUSE)
            }
        })

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, // Your pause icon
                "Pause",
                pauseIntent
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, // Your play icon
                "Play",
                playIntent
            )
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Focus by Noise")
            .setStyle(mediaStyle)
            .setContentIntent(pendingMainIntent)
            .addAction(playPauseAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return notification
    }
}
