package com.example.campusmate.ui.nfc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.UserProfile
import com.example.campusmate.data.repository.UserProfileRepository
import com.example.campusmate.domain.nfc.NfcPayloadWriter
import com.example.campusmate.domain.nfc.NfcUtils
import com.example.campusmate.ui.profile.ProfileUiFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject

/** Shares the public study-card JSON through NFC NDEF. */
class NfcShareActivity : AppCompatActivity() {
    private lateinit var repository: UserProfileRepository
    private lateinit var rootView: View
    private var ndefMessage: NdefMessage? = null
    private var sharingEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_share)
        repository = UserProfileRepository(this)
        rootView = findViewById(R.id.nfcShareRoot)

        setupToolbar()
        setupActions()
        bindProfilePayload()
        refreshNfcState()
    }

    override fun onResume() {
        super.onResume()
        refreshNfcState()
        if (sharingEnabled) {
            enableNfcSharing()
        }
    }

    override fun onPause() {
        disableForegroundDispatch()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTagWrite(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.nfcShareToolbar)
        toolbar.title = getString(R.string.nfc_share_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.nfcSettingsButton).setOnClickListener {
            openNfcSettings()
        }
        findViewById<MaterialButton>(R.id.enableNfcShareButton).setOnClickListener {
            confirmEnableSharing()
        }
    }

    private fun bindProfilePayload() {
        val profile = repository.getProfile()
        if (profile == null) {
            findViewById<TextView>(R.id.nfcShareStatusText).setText(R.string.nfc_share_failed)
            findViewById<MaterialButton>(R.id.enableNfcShareButton).isEnabled = false
            return
        }

        val json = runCatching { repository.buildPublicProfileJson() }.getOrElse {
            findViewById<TextView>(R.id.nfcShareStatusText).setText(R.string.nfc_share_failed)
            findViewById<MaterialButton>(R.id.enableNfcShareButton).isEnabled = false
            return
        }
        ndefMessage = NfcPayloadWriter.createProfileMessage(json)
        findViewById<TextView>(R.id.nfcShareProfileText).text = profileShareSummary(profile)
        findViewById<TextView>(R.id.nfcShareJsonText).text = prettyJson(json)
    }

    private fun refreshNfcState() {
        val statusText = findViewById<TextView>(R.id.nfcShareStatusText)
        val infoText = findViewById<TextView>(R.id.nfcShareInfoText)
        val settingsButton = findViewById<MaterialButton>(R.id.nfcSettingsButton)
        val shareButton = findViewById<MaterialButton>(R.id.enableNfcShareButton)

        when {
            !NfcUtils.isSupported(this) -> {
                statusText.setText(R.string.nfc_not_supported)
                infoText.setText(R.string.profile_public_scope_body)
                settingsButton.visibility = View.GONE
                shareButton.isEnabled = false
            }
            !NfcUtils.isEnabled(this) -> {
                statusText.setText(R.string.nfc_not_enabled)
                infoText.setText(R.string.nfc_share_confirm_body)
                settingsButton.visibility = View.VISIBLE
                shareButton.isEnabled = false
            }
            sharingEnabled -> {
                statusText.setText(R.string.nfc_share_ready)
                infoText.setText(R.string.nfc_share_confirm_body)
                settingsButton.visibility = View.GONE
                shareButton.isEnabled = ndefMessage != null
            }
            else -> {
                statusText.setText(R.string.nfc_share_confirm_title)
                infoText.setText(R.string.nfc_share_confirm_body)
                settingsButton.visibility = View.GONE
                shareButton.isEnabled = ndefMessage != null
            }
        }
    }

    private fun confirmEnableSharing() {
        if (ndefMessage == null) {
            Snackbar.make(rootView, R.string.nfc_share_failed, Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nfc_share_confirm_title)
            .setMessage(R.string.nfc_share_confirm_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.profile_nfc_share_action) { _, _ ->
                sharingEnabled = true
                enableNfcSharing()
                refreshNfcState()
                Snackbar.make(rootView, R.string.nfc_share_ready, Snackbar.LENGTH_LONG).show()
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun enableNfcSharing() {
        NfcUtils.getAdapter(this)?.enableForegroundDispatch(
            this,
            NfcUtils.createForegroundDispatchIntent(this),
            NfcUtils.createProfileIntentFilters(),
            null
        )
    }

    private fun disableForegroundDispatch() {
        runCatching { NfcUtils.getAdapter(this)?.disableForegroundDispatch(this) }
    }

    private fun handleTagWrite(intent: Intent) {
        if (!sharingEnabled) return
        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        val message = ndefMessage ?: return
        val result = NfcPayloadWriter.writeToTag(tag, message)
        val messageRes = if (result == NfcPayloadWriter.WriteResult.SUCCESS) {
            R.string.nfc_share_tag_write_success
        } else {
            R.string.nfc_share_tag_write_failed
        }
        Snackbar.make(rootView, messageRes, Snackbar.LENGTH_LONG).show()
    }

    private fun openNfcSettings() {
        try {
            startActivity(NfcUtils.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(rootView, R.string.settings_system_entry_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun profileShareSummary(profile: UserProfile): String {
        return listOf(
            profile.nickname,
            ProfileUiFormatter.schoolLine(profile.school, profile.major, profile.grade),
            ProfileUiFormatter.optional(profile.bio)
        ).joinToString(separator = "\n")
    }

    private fun prettyJson(json: String): String {
        return runCatching { JSONObject(json).toString(2) }.getOrDefault(json)
    }
}
