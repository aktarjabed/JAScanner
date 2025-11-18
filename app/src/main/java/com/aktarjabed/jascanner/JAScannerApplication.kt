package com.aktarjabed.jascanner

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader
import timber.log.Timber

class JAScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization failed!")
        } else {
            Log.d("OpenCV", "Initialized successfully")
        }
    }
}