package com.shade.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.shade.app.data.remote.websocket.MessageListener
import com.shade.app.data.remote.websocket.ShadeWebSocketManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShadeApp : Application(), DefaultLifecycleObserver, Configuration.Provider {
    
    @Inject
    lateinit var webSocketManager: ShadeWebSocketManager
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var messageListener: MessageListener

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        webSocketManager.disconnect()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        messageListener.ensureConnected()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
