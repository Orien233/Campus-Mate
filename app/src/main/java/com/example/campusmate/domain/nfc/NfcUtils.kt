package com.example.campusmate.domain.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.provider.Settings

/** NFC capability, settings, and foreground-dispatch helpers. */
object NfcUtils {
    fun getAdapter(context: Context): NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    fun isSupported(context: Context): Boolean = getAdapter(context) != null

    fun isEnabled(context: Context): Boolean = getAdapter(context)?.isEnabled == true

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NFC_SETTINGS)

    fun createForegroundDispatchIntent(activity: Activity): PendingIntent {
        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun createProfileIntentFilters(): Array<IntentFilter> {
        return arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataType(NfcPayloadWriter.MIME_TYPE)
            },
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        )
    }
}
