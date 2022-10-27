package com.ubt.robocontroller

import android.app.Application
import timber.log.Timber

class RoboApp : Application() {
    override fun onCreate() {
        super.onCreate()

//        CrashHandler.getInstance().init(applicationContext)

        Timber.plant(Timber.DebugTree())
    }
}