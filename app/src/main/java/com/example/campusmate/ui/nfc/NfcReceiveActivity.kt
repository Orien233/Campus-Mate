package com.example.campusmate.ui.nfc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.data.repository.StudyBuddyRepository
import com.example.campusmate.data.repository.UserProfileRepository
import com.example.campusmate.domain.nfc.NfcCardPayload
import com.example.campusmate.domain.nfc.NfcPayloadParser
import com.example.campusmate.domain.nfc.NfcUtils
import com.example.campusmate.ui.profile.ProfileUiFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject

/** Receives CampusMate profile NDEF messages, previews them, then saves confirmed buddies. */
class NfcReceiveActivity : AppCompatActivity() {
    private lateinit var profileRepository: UserProfileRepository
    private lateinit var buddyRepository: StudyBuddyRepository
    private lateinit var parser: NfcPayloadParser
    private lateinit var rootView: View
    private var pendingPayload: NfcCardPayload? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_receive)
        profileRepository = UserProfileRepository(this)
        buddyRepository = StudyBuddyRepository(this)
        parser = NfcPayloadParser(profileRepository)
        rootView = findViewById(R.id.nfcReceiveRoot)

        setupToolbar()
        setupActions()
        refreshNfcState()
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshNfcState()
        enableForegroundDispatch()
    }

    override fun onPause() {
        disableForegroundDispatch()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.nfcReceiveToolbar)
        toolbar.title = getString(R.string.nfc_receive_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.nfcReceiveSettingsButton).setOnClickListener {
            openNfcSettings()
        }
        findViewById<MaterialButton>(R.id.confirmNfcBuddyButton).setOnClickListener {
            confirmAddBuddy()
        }
    }

    private fun refreshNfcState() {
        val statusText = findViewById<TextView>(R.id.nfcReceiveStatusText)
        val settingsButton = findViewById<MaterialButton>(R.id.nfcReceiveSettingsButton)
        when {
            !NfcUtils.isSupported(this) -> {
                statusText.setText(R.string.nfc_not_supported)
                settingsButton.visibility = View.GONE
            }
            !NfcUtils.isEnabled(this) -> {
                statusText.setText(R.string.nfc_not_enabled)
                settingsButton.visibility = View.VISIBLE
            }
            else -> {
                statusText.setText(R.string.nfc_receive_waiting)
                settingsButton.visibility = View.GONE
            }
        }
    }

    private fun enableForegroundDispatch() {
        if (!NfcUtils.isSupported(this) || !NfcUtils.isEnabled(this)) return
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

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action !in SUPPORTED_NFC_ACTIONS) return
        val messages = getNdefMessages(intent)
        if (messages.isEmpty()) {
            Snackbar.make(rootView, R.string.nfc_receive_parse_failed, Snackbar.LENGTH_LONG).show()
            return
        }

        val payload = messages.firstNotNullOfOrNull { message ->
            runCatching { parser.parse(message) }.getOrNull()
        }
        if (payload == null) {
            Snackbar.make(rootView, R.string.nfc_receive_parse_failed, Snackbar.LENGTH_LONG).show()
            return
        }
        pendingPayload = payload
        bindPreview(payload)
    }

    private fun getNdefMessages(intent: Intent): List<NdefMessage> {
        val directMessages = intent
            .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?.toList()
            .orEmpty()
        if (directMessages.isNotEmpty()) return directMessages

        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return emptyList()
        val cachedMessage = Ndef.get(tag)?.cachedNdefMessage ?: return emptyList()
        return listOf(cachedMessage)
    }

    private fun bindPreview(payload: NfcCardPayload) {
        val buddy = payload.buddy
        findViewById<MaterialCardView>(R.id.nfcReceivePreviewCard).visibility = View.VISIBLE
        findViewById<TextView>(R.id.nfcReceiveNameText).text = buddy.nickname
        findViewById<TextView>(R.id.nfcReceiveSchoolText).text =
            ProfileUiFormatter.schoolLine(buddy.school, buddy.major, buddy.grade)
        findViewById<TextView>(R.id.nfcReceiveBioText).text = buddySummary(buddy)
        findViewById<TextView>(R.id.nfcReceiveJsonText).text = prettyJson(payload.rawJson)
        findViewById<TextView>(R.id.nfcReceiveDuplicateText).visibility =
            if (buddyRepository.existsByGithubOrEmail(buddy.github, buddy.email)) View.VISIBLE else View.GONE
    }

    private fun confirmAddBuddy() {
        val buddy = pendingPayload?.buddy ?: return
        val id = runCatching { buddyRepository.addBuddy(buddy) }.getOrDefault(-1L)
        if (id > 0L) {
            pendingPayload = null
            findViewById<MaterialCardView>(R.id.nfcReceivePreviewCard).visibility = View.GONE
            Snackbar.make(rootView, R.string.buddy_added_success, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(rootView, R.string.buddy_add_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openNfcSettings() {
        try {
            startActivity(NfcUtils.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(rootView, R.string.settings_system_entry_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun buddySummary(buddy: StudyBuddy): String {
        return listOf(
            ProfileUiFormatter.optional(buddy.bio),
            ProfileUiFormatter.optional(buddy.github),
            ProfileUiFormatter.optional(buddy.email),
            ProfileUiFormatter.optional(buddy.phone)
        ).joinToString(separator = "\n")
    }

    private fun prettyJson(json: String): String {
        return runCatching { JSONObject(json).toString(2) }.getOrDefault(json)
    }

    companion object {
        private val SUPPORTED_NFC_ACTIONS = setOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED
        )
    }
}
