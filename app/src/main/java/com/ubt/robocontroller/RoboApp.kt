package com.ubt.robocontroller

import android.app.Application
import timber.log.Timber

class RoboApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
    }
}