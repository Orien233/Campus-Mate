package com.example.campusmate.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.campusmate.util.NotificationUtils
import com.example.campusmate.util.SystemBarsInsets

/** Application entry for process-wide CampusMate initialization. */
class CampusMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationUtils.ensureTaskReminderChannel(this)
        NotificationUtils.ensureFocusServiceChannel(this)
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    SystemBarsInsets.apply(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
