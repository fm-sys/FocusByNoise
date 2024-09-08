package com.fmsys.focusbynoise

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.SoundPool
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.widget.Toast
import com.fmsys.focusbynoise.helpers.AUDIO_BECOMING_NOISY
import com.fmsys.focusbynoise.helpers.CALL_ENDED
import com.fmsys.focusbynoise.helpers.CALL_STARTED
import com.fmsys.focusbynoise.helpers.CONNECTION_STATE_CHANGED
import com.fmsys.focusbynoise.helpers.DISMISS
import com.fmsys.focusbynoise.helpers.HEADPHONES_CONNECTED
import com.fmsys.focusbynoise.helpers.HEADSET_PLUG
import com.fmsys.focusbynoise.helpers.HEADSET_STATE_CHANGED
import com.fmsys.focusbynoise.helpers.PAUSE
import com.fmsys.focusbynoise.helpers.PLAY
import com.fmsys.focusbynoise.helpers.SET_PAUSED
import com.fmsys.focusbynoise.helpers.SET_PLAYING
import com.fmsys.focusbynoise.helpers.TOGGLE_PLAY


class PlayerService : Service(), SoundPool.OnLoadCompleteListener {

    private val binder = LocalBinder()
    var mActivity: Callbacks? = null

    var prefs: SharedPreferences? = null
    var currentNoise: String? = null
    var soundPool: SoundPool? = null
    var soundID: Int = -1
    var streamID: Int? = -1
    var isLoading: Boolean = false
    var streamLoaded: Boolean = false
    private var isPlaying: Boolean = false
    var wasPlaying: Boolean = false
    var lastAction: String? = null
    var notificationUtils: NotificationUtils? = null
    var audioAttributes: AudioAttributes? = null
    lateinit var audioManger: AudioManager

    var onPhoneCall = false
    var audioIsNoisy = false

    var outsidePauseReceiver: OutsidePauseReceiver? = null

    var audioFocusChangeListener: OnAudioFocusChangeListener =
        OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                play()
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                pause()
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                pause()
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
                play()
            }
        }

    companion object {
        fun start(context: Context, action: String): Boolean {
            Intent(context, PlayerService::class.java).setAction(action).run {
                if (Build.VERSION.SDK_INT < 26) context.startService(this)
                else context.startForegroundService(this)
            }
            return true
        }
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): PlayerService = this@PlayerService
    }

    fun registerClient(activity: Activity) {
        mActivity = activity as Callbacks
    }

    // callbacks interface for communication with service clients!
    interface Callbacks {
        fun updateClient(action: String)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        audioManger = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        wasPlaying = getPrefs().getBoolean("wasPlaying", false)
        outsidePauseReceiver = OutsidePauseReceiver()
        val filter = IntentFilter()
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        filter.addAction(HEADSET_STATE_CHANGED)
        filter.addAction(CONNECTION_STATE_CHANGED)
        filter.addAction(HEADSET_PLUG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(outsidePauseReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(outsidePauseReceiver, filter)
        }
        createNotification(wasPlaying)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null) {
            return START_NOT_STICKY
        }

        val action = intent.action
        when (action) {
            PLAY -> play()
            PAUSE -> pause()
            TOGGLE_PLAY -> togglePlay()
            DISMISS -> dismiss()
            CALL_STARTED -> {
                onPhoneCall = true
                if (isPlaying) pause(false)
            }

            CALL_ENDED -> {
                onPhoneCall = false
                if (wasPlaying && !audioIsNoisy) play(false)
            }

            AUDIO_BECOMING_NOISY -> {
                audioIsNoisy = true
                if (isPlaying) pause(false)
            }

            HEADPHONES_CONNECTED -> {
                audioIsNoisy = false
                if (wasPlaying && !onPhoneCall) play(false)
            }
        }

        return START_NOT_STICKY
    }

    fun createNotification(isPlaying: Boolean) {
        if (notificationUtils == null) {
            notificationUtils = NotificationUtils(this)
        }
        val notification = notificationUtils?.createNotification(isPlaying)
        startForeground(NotificationUtils.NOTIFICATION_ID, notification)
    }

    fun updateWidget(toIsPlaying: Boolean) {
        val newIntent = Intent(this, EasyNoiseWidget::class.java)
        if (toIsPlaying) {
            newIntent.setAction(SET_PLAYING)
        } else {
            newIntent.setAction(SET_PAUSED)
        }
        sendBroadcast(newIntent)
    }

    override fun onDestroy() {
        pause()
        unregisterReceiver(outsidePauseReceiver)
        if (streamID != null && streamID!! > 0) {
            soundPool?.stop(streamID!!)
        }
        soundPool?.release()
        soundPool = null
        isPlaying = false
        streamLoaded = false
        super.onDestroy()
    }

    fun initSoundPool() {
        if (isLoading) return
        isLoading = true
        if (soundPool == null) {
            audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build() as SoundPool
            soundPool!!.setOnLoadCompleteListener(this)
        }
        if (!streamLoaded) {
            loadNoise()
        }
    }

    fun loadNoise() {
        val noise = getPrefs().getString("noise", "pink")
        currentNoise = noise
        val resource: Int = when (noise) {
            resources.getString(R.string.fuzz) -> R.raw.fuzz
            resources.getString(R.string.gray) -> R.raw.grey_noise
            resources.getString(R.string.gray_2) -> R.raw.grey_noise_2
            resources.getString(R.string.white) -> R.raw.white_noise
            resources.getString(R.string.pink) -> R.raw.pink_noise
            resources.getString(R.string.brown) -> R.raw.brown_noise
            resources.getString(R.string.blue) -> R.raw.blue_noise
            else -> -1
        }
        if (resource > 0) {
            soundID = soundPool!!.load(this, resource, 1)
        }
    }

    override fun onLoadComplete(pSoundPool: SoundPool, pSampleID: Int, status: Int) {
        streamLoaded = (soundID > 0)
        isLoading = false
        if (streamLoaded) {
            if (lastAction.equals("play")) playLoaded()
        } else {
            val toast = Toast.makeText(
                applicationContext,
                resources.getString(R.string.load_error),
                Toast.LENGTH_SHORT
            )
            toast.show()
        }
    }

    fun playLoaded() {
        var ok = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes!!)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            val result: Int = audioManger.requestAudioFocus(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                ok = true
            }
        } else {
            ok = true
        }

        if (ok) {
            streamID = soundPool?.play(soundID, 1f, 1f, 1, -1, 1.0F)
            isPlaying = true
        }
    }

    fun getIsPlaying(): Boolean {
        return isPlaying
    }

    fun togglePlay() {
        if (!isPlaying && !isLoading) {
            play()
        } else {
            pause()
        }
    }

    fun play(doUpdatePref: Boolean = true) {
        lastAction = "play"
        if (streamLoaded) {
            playLoaded()
        } else {
            initSoundPool()
        }
        onPlayChanged(true, doUpdatePref)
    }

    fun pause(doUpdatePref: Boolean = true) {
        lastAction = "pause"
        soundPool?.autoPause()
        isPlaying = false
        onPlayChanged(false, doUpdatePref)
    }

    fun onPlayChanged(toPlaying: Boolean, doUpdatePref: Boolean) {
        mActivity?.updateClient(if (toPlaying) PLAY else PAUSE)
        updateWidget(toPlaying)
        createNotification(toPlaying)
        getPrefs().edit().putBoolean("isPlaying", toPlaying).apply()
        if (doUpdatePref) {
            wasPlaying = toPlaying
            getPrefs().edit().putBoolean("wasPlaying", toPlaying).apply()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(
                this,
                ComponentName(this, QSTileService::class.java.getName())
            )
        }
    }

    fun dismiss() {
        mActivity?.updateClient(DISMISS)
        pause()
        stopForeground(true)
        stopSelf()
    }

    @JvmName("getPrefs1")
    fun getPrefs(): SharedPreferences {
        if (prefs == null) {
            prefs = getSharedPreferences(applicationContext.packageName, 0)
        }
        return prefs!!
    }

    fun noiseChanged() {
        val newNoise = getPrefs().getString("noise", "pink")
        if (newNoise.equals(currentNoise)) return
        val tempIsPlaying = isPlaying
        if (tempIsPlaying) pause(false)
        if (streamLoaded) {
            soundPool?.stop(streamID!!)
            soundPool?.unload(soundID)
            streamLoaded = false
        }
        if (tempIsPlaying) play(false)
    }
}
