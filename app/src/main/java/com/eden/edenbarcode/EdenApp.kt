package com.eden.edenbarcode

import android.app.Application
import android.util.Log
import com.eden.edenbarcode.di.AppComponent
import com.eden.edenbarcode.di.DaggerAppComponent

/**
 * ----
 * Created by Лукащук Олег(oleg) on 13.01.19.
 */
class EdenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("EdenApp", "onCreate")
        appComponent = DaggerAppComponent.builder()
            .application(this)
            .build()
    }

    companion object {
        lateinit var appComponent: AppComponent
    }
}