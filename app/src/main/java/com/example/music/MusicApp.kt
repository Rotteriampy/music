package com.arotter.music

import android.app.Activity
import android.app.Application
import android.os.Bundle

class MusicApp : Application() {
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    private var foregroundStartAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("player_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains("duck_action_loss")) editor.putString("duck_action_loss", "pause")
        if (!prefs.contains("duck_action_loss_transient")) editor.putString("duck_action_loss_transient", "duck")
        if (!prefs.contains("duck_action_can_duck")) editor.putString("duck_action_can_duck", "duck")
        if (!prefs.contains("duck_level")) editor.putFloat("duck_level", 0.3f)
        if (!prefs.contains("enable_ducking")) editor.putBoolean("enable_ducking", true)
        editor.apply()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}

            override fun onActivityStarted(activity: Activity) {
                if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                    // App entered foreground
                    foregroundStartAt = System.currentTimeMillis()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                isActivityChangingConfigurations = activity.isChangingConfigurations
                if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                    // App entered background
                    if (foregroundStartAt > 0L) {
                        val sessionMs = System.currentTimeMillis() - foregroundStartAt
                        val prefs = getSharedPreferences("app_usage", MODE_PRIVATE)
                        val total = prefs.getLong("total_ms", 0L) + sessionMs
                        prefs.edit().putLong("total_ms", total).apply()
                        foregroundStartAt = 0L
                    }
                }
            }
        })
    }
}
