package com.arotter.music

import android.app.Application
import android.content.Context

class MusicApplication : Application() {
    companion object {
        lateinit var context: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }
}