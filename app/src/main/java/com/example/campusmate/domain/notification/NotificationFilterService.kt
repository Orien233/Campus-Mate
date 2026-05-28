package com.example.campusmate.domain.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationFilterService : NotificationListenerService() {

    companion object {
        private val allowedPackages = setOf(
            "com.example.campusmate",
            "com.android.systemui",
            "com.android.phone",
            "com.android.contacts"
        )

        @Volatile
        var isFocusModeActive: Boolean = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return super.onNotificationPosted(sbn)

        if (!isFocusModeActive) {
            return super.onNotificationPosted(sbn)
        }

        val packageName = sbn.packageName
        if (packageName !in allowedPackages) {
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                // Ignore cancellation errors
            }
        }

        super.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    fun setFocusMode(active: Boolean) {
        isFocusModeActive = active
    }
}
