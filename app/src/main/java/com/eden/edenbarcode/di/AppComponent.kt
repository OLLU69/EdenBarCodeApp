@file:Suppress("unused")

package com.eden.edenbarcode.di

import android.app.Application
import android.content.Context
import com.eden.edenbarcode.model.EdenModel
import com.eden.edenbarcode.model.RoomEdenModel
import com.eden.edenbarcode.qr.BarcodeRecognizer
import com.eden.edenbarcode.qr.CameraWrapper
import com.eden.edenbarcode.qr.IBarcodeRecognizer
import com.eden.edenbarcode.qr.ICameraWrapper
import com.eden.edenbarcode.repository.Repository
import com.eden.edenbarcode.repository.RoomRepository
import com.eden.edenbarcode.ui.MainViewModel
import com.eden.edenbarcode.ui.ScanActivity
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import javax.inject.Singleton

/**
 * ----
 * Created by Лукащук Олег(oleg) on 15.01.19.
 */
@Singleton
@Component(modules = [AppModule::class, ModelModule::class, ScannerModule::class])
interface AppComponent {
    fun inject(model: MainViewModel)
    fun inject(scanActivity: ScanActivity)
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(app: Application): Builder

        fun build(): AppComponent
    }
}

@Module
interface AppModule {
    @Singleton
    @Binds
    fun getAppContext(application: Application): Context
}

@Module
interface ModelModule {
    @Singleton
    @Binds
    fun getModel(model: RoomEdenModel): EdenModel

    @Singleton
    @Binds
    fun getRepository(repository: RoomRepository): Repository
}

@Module
interface ScannerModule {
    @Singleton
    @Binds
    fun getCamera(camera: CameraWrapper): ICameraWrapper

    @Singleton
    @Binds
    fun getScannerRecognizer(recognizer: BarcodeRecognizer): IBarcodeRecognizer
}

