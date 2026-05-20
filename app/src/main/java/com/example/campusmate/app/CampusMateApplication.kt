package com.example.campusmate.app

import android.app.Application
import com.example.campusmate.util.NotificationUtils

/** Application entry for process-wide CampusMate initialization. */
class CampusMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationUtils.ensureTaskReminderChannel(this)
        NotificationUtils.ensureFocusServiceChannel(this)
    }
}
