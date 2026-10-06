package com.nyxulrix.budgeter

import android.app.Application
import com.nyxulrix.budgeter.data.Store
import com.nyxulrix.budgeter.sync.SyncWorker
import com.nyxulrix.budgeter.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        store = Store(filesDir, scope)
        store.onChange = {
            Widgets.refresh(this)
            SyncWorker.queue(this)
        }
        Widgets.scheduleMidnight(this)
    }

    companion object {
        lateinit var app: App
        lateinit var store: Store
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
