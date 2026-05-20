package com.example.campusmate.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.data.repository.StudyBuddyRepository
import com.example.campusmate.data.repository.UserProfileRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Camera QR scanner that previews CampusMate profile JSON before saving a buddy. */
class ScanQrActivity : AppCompatActivity() {
    private lateinit var profileRepository: UserProfileRepository
    private lateinit var buddyRepository: StudyBuddyRepository
    private lateinit var rootView: View
    private var pendingBuddy: StudyBuddy? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchScanner()
            } else {
                Snackbar.make(rootView, R.string.scan_qr_permission_denied, Snackbar.LENGTH_LONG).show()
            }
        }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            Snackbar.make(rootView, R.string.scan_qr_cancelled, Snackbar.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        handleScanResult(contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)
        profileRepository = UserProfileRepository(this)
        buddyRepository = StudyBuddyRepository(this)
        rootView = findViewById(R.id.scanQrRoot)

        setupToolbar()
        findViewById<MaterialButton>(R.id.startScanButton).setOnClickListener {
            requestCameraAndScan()
        }
        findViewById<MaterialButton>(R.id.confirmAddBuddyButton).setOnClickListener {
            confirmAddBuddy()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.scanQrToolbar)
        toolbar.title = getString(R.string.scan_qr_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun requestCameraAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scan_qr_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val buddy = runCatching {
            profileRepository.parsePublicProfileJson(contents, StudyBuddy.SOURCE_QR)
        }.getOrElse {
            Snackbar.make(rootView, R.string.scan_qr_parse_failed, Snackbar.LENGTH_LONG).show()
            return
        }
        pendingBuddy = buddy
        bindPreview(buddy)
    }

    private fun bindPreview(buddy: StudyBuddy) {
        findViewById<MaterialCardView>(R.id.scanPreviewCard).visibility = View.VISIBLE
        findViewById<TextView>(R.id.scanPreviewNameText).text = buddy.nickname
        findViewById<TextView>(R.id.scanPreviewSchoolText).text =
            ProfileUiFormatter.schoolLine(buddy.school, buddy.major, buddy.grade)
        findViewById<TextView>(R.id.scanPreviewBioText).text = buildString {
            append(ProfileUiFormatter.optional(buddy.bio))
            append("\nGitHub：").append(ProfileUiFormatter.optional(buddy.github))
            append("\n邮箱：").append(ProfileUiFormatter.optional(buddy.email))
            append("\n手机号：").append(ProfileUiFormatter.optional(buddy.phone))
        }
        findViewById<TextView>(R.id.scanDuplicateText).visibility =
            if (buddyRepository.existsByGithubOrEmail(buddy.github, buddy.email)) View.VISIBLE else View.GONE
    }

    private fun confirmAddBuddy() {
        val buddy = pendingBuddy ?: return
        val id = runCatching { buddyRepository.addBuddy(buddy) }.getOrDefault(-1L)
        if (id > 0L) {
            Snackbar.make(rootView, R.string.buddy_added_success, Snackbar.LENGTH_SHORT).show()
            pendingBuddy = null
            findViewById<MaterialCardView>(R.id.scanPreviewCard).visibility = View.GONE
        } else {
            Snackbar.make(rootView, R.string.buddy_add_failed, Snackbar.LENGTH_SHORT).show()
        }
    }
}
