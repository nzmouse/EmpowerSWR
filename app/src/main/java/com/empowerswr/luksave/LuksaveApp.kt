package com.empowerswr.luksave

import android.app.Application
import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics

class LuksaveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.d("LuksaveApp: Initialized")
    }
}
class ProductionTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == android.util.Log.VERBOSE || priority == android.util.Log.DEBUG) {
            return // Skip verbose/debug logs in production
        }
        android.util.Log.println(priority, tag, message)
        t?.let { FirebaseCrashlytics.getInstance().recordException(it) }
    }
}
