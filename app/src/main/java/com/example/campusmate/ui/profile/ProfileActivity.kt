package com.example.campusmate.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.UserProfile
import com.example.campusmate.data.repository.UserProfileRepository
import com.example.campusmate.ui.buddy.BuddyListActivity
import com.example.campusmate.ui.nfc.NfcReceiveActivity
import com.example.campusmate.ui.nfc.NfcShareActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/** Entry screen for the local study card, QR card, scanner, and buddy list. */
class ProfileActivity : AppCompatActivity() {
    private lateinit var repository: UserProfileRepository
    private lateinit var rootView: View
    private var currentProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        repository = UserProfileRepository(this)
        rootView = findViewById(R.id.profileRoot)

        setupToolbar()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.profileToolbar)
        toolbar.title = getString(R.string.profile_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.editProfileButton).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.showQrButton).setOnClickListener {
            if (currentProfile == null) {
                Snackbar.make(rootView, R.string.profile_qr_generate_failed, Snackbar.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, StudyCardActivity::class.java))
            }
        }
        findViewById<MaterialButton>(R.id.scanQrButton).setOnClickListener {
            startActivity(Intent(this, ScanQrActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.nfcShareButton).setOnClickListener {
            if (currentProfile == null) {
                Snackbar.make(rootView, R.string.nfc_share_failed, Snackbar.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, NfcShareActivity::class.java))
            }
        }
        findViewById<MaterialButton>(R.id.nfcReceiveButton).setOnClickListener {
            startActivity(Intent(this, NfcReceiveActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buddyListButton).setOnClickListener {
            startActivity(Intent(this, BuddyListActivity::class.java))
        }
    }

    private fun loadProfile() {
        val profile = repository.getProfile()
        currentProfile = profile
        if (profile == null) {
            bindEmptyProfile()
        } else {
            bindProfile(profile)
        }
    }

    private fun bindEmptyProfile() {
        findViewById<TextView>(R.id.profileNameText).text = getString(R.string.profile_empty_title)
        findViewById<TextView>(R.id.profileSchoolText).text = ""
        findViewById<TextView>(R.id.profileBioText).text = getString(R.string.profile_empty_body)
        findViewById<TextView>(R.id.profileContactText).text = ""
        findViewById<TextView>(R.id.profileEmptyText).visibility = View.GONE
        findViewById<MaterialButton>(R.id.editProfileButton).setText(R.string.profile_create_action)
        findViewById<MaterialButton>(R.id.showQrButton).isEnabled = false
        findViewById<MaterialButton>(R.id.nfcShareButton).isEnabled = false
    }

    private fun bindProfile(profile: UserProfile) {
        findViewById<TextView>(R.id.profileNameText).text = profile.nickname
        findViewById<TextView>(R.id.profileSchoolText).text =
            ProfileUiFormatter.schoolLine(profile.school, profile.major, profile.grade)
        findViewById<TextView>(R.id.profileBioText).text = ProfileUiFormatter.optional(profile.bio)
        findViewById<TextView>(R.id.profileContactText).text = buildString {
            append("GitHub：")
            append(ProfileUiFormatter.optional(profile.github))
            append('\n')
            append("邮箱：")
            append(if (profile.showEmail) ProfileUiFormatter.optional(profile.email) else getString(R.string.profile_public_contact_hidden))
            append('\n')
            append("手机号：")
            append(if (profile.showPhone) ProfileUiFormatter.optional(profile.phone) else getString(R.string.profile_public_contact_hidden))
        }
        findViewById<TextView>(R.id.profileEmptyText).visibility = View.GONE
        findViewById<MaterialButton>(R.id.editProfileButton).setText(R.string.profile_edit_action)
        findViewById<MaterialButton>(R.id.showQrButton).isEnabled = true
        findViewById<MaterialButton>(R.id.nfcShareButton).isEnabled = true
    }
}
