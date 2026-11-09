package com.spaceclipboard.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.spaceclipboard.app.di.AppContainer

class SpaceClipboardApplication : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        container.receiverController.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        container.receiverController.stop()
    }
}
