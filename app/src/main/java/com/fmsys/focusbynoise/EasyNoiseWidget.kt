package com.fmsys.focusbynoise

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.fmsys.focusbynoise.helpers.*
import android.app.PendingIntent
import android.content.ComponentName

/**
 * Implementation of App Widget functionality.
 */
class EasyNoiseWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        when (action) {
            TOGGLE_PLAY -> togglePlay(context)
            SET_PLAYING -> setPlaying(context, true)
            SET_PAUSED -> setPlaying(context, false)
        }
        super.onReceive(context, intent)
    }

    fun togglePlay(context: Context?) {
        if (context == null) {
            return
        }
        PlayerService.start(context, TOGGLE_PLAY)
    }

    fun setPlaying(context: Context?, isPlaying: Boolean) {
        if (context == null) {
            return
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, EasyNoiseWidget::class.java))
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, isPlaying)
        }
    }
}

@SuppressLint("RemoteViewLayout")
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    _isPlaying: Boolean? = null
) {
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.easy_noise_widget)

    var isPlaying: Boolean? = _isPlaying
    if (isPlaying == null) {
        isPlaying = context.getSharedPreferences(context.packageName, 0)
            .getBoolean("isPlaying", false)
    }
    if (isPlaying) {
        views.setViewVisibility(R.id.playIcon, View.INVISIBLE)
        views.setViewVisibility(R.id.pauseIcon, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.pauseIcon, View.INVISIBLE)
        views.setViewVisibility(R.id.playIcon, View.VISIBLE)
    }

    val togglePlayIntent = Intent(context, EasyNoiseWidget::class.java)
    togglePlayIntent.setAction(TOGGLE_PLAY)
    val pendingTogglePlayIntent = PendingIntent.getBroadcast(context, 0, togglePlayIntent, PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.widgetLayout, pendingTogglePlayIntent)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
