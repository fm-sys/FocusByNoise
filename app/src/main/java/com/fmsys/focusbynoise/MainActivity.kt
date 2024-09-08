package com.fmsys.focusbynoise

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.fmsys.focusbynoise.helpers.DISMISS
import com.fmsys.focusbynoise.helpers.PAUSE
import com.fmsys.focusbynoise.helpers.PLAY
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), PlayerService.Callbacks {

    lateinit var playButton: ImageButton
    lateinit var pauseButton: ImageButton
    lateinit var noiseChooserButton: Button
    lateinit var noises: Array<String>
    lateinit var prefs: SharedPreferences

    private lateinit var playerService: PlayerService
    private var serviceIsBound: Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to PlayerService, cast the IBinder and get PlayerService instance
            val binder = service as PlayerService.LocalBinder
            playerService = binder.getService()
            playerService.registerClient(this@MainActivity)
            serviceIsBound = true
            setButtonsVisibility(playerService.getIsPlaying())
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceIsBound = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.main_toolbar, menu)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playButton = findViewById(R.id.playButton) as ImageButton
        pauseButton = findViewById(R.id.pauseButton) as ImageButton

        prefs = getSharedPreferences(applicationContext.packageName, 0)

        val noise = prefs.getString("noise", "pink")

        noises = arrayOf(
            resources.getString(R.string.fuzz),
            resources.getString(R.string.gray),
            resources.getString(R.string.gray_2),
            resources.getString(R.string.white),
            resources.getString(R.string.pink),
            resources.getString(R.string.brown),
            resources.getString(R.string.blue),
        )

        noiseChooserButton = findViewById(R.id.noiseChooserButton)
        noiseChooserButton.text = noise
        noiseChooserButton.setOnClickListener { v ->
            MaterialAlertDialogBuilder(this)
                .setItems(noises) { dialog, which ->
                    val newNoise = noises[which]
                    prefs.edit().putString("noise", newNoise).apply()
                    noiseChooserButton.text = newNoise
                    if (serviceIsBound) {
                        playerService.noiseChanged()
                    }
                }
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to LocalService
        Intent(this, PlayerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        serviceIsBound = false
    }


    fun play(@Suppress("UNUSED_PARAMETER")view: View) {
        val intent = Intent(this@MainActivity, NotificationReceiver::class.java)
        intent.setAction(PLAY)
        sendBroadcast(intent)
    }

    fun pause(@Suppress("UNUSED_PARAMETER")view: View) {
        val intent = Intent(this@MainActivity, NotificationReceiver::class.java)
        intent.setAction(PAUSE)
        sendBroadcast(intent)
    }

    override fun updateClient(action: String) {
        when (action) {
            PLAY -> setButtonsVisibility(true)
            PAUSE -> setButtonsVisibility(false)
            DISMISS -> this.finishAndRemoveTask()
        }
    }

    fun setButtonsVisibility(isPlaying: Boolean) {
        if (isPlaying) {
            pauseButton.setVisibility(View.VISIBLE)
            playButton.setVisibility(View.GONE)
        } else {
            playButton.setVisibility(View.VISIBLE)
            pauseButton.setVisibility(View.GONE)
        }
    }

}
