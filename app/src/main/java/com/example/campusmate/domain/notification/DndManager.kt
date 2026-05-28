package com.example.campusmate.domain.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class DndManager(private val context: Context) {
    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun isDndPolicyAccessGranted(): Boolean {
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun requestPolicyAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    fun enableDnd(): Boolean {
        if (!isDndPolicyAccessGranted()) {
            return false
        }

        return try {
            notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun disableDnd(): Boolean {
        if (!isDndPolicyAccessGranted()) {
            return false
        }

        return try {
            notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun getCurrentInterruptionFilter(): Int {
        return notificationManager?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun isDndCurrentlyEnabled(): Boolean {
        return getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_NONE
    }
}
