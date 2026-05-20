package com.example.campusmate.ui.profile

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.UserProfile
import com.example.campusmate.data.repository.UserProfileRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject

/** Generates a QR bitmap from the same public profile JSON used by future NFC sharing. */
class StudyCardActivity : AppCompatActivity() {
    private lateinit var repository: UserProfileRepository
    private lateinit var rootView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_card)
        repository = UserProfileRepository(this)
        rootView = findViewById(R.id.studyCardRoot)

        setupToolbar()
        bindQrCard()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.studyCardToolbar)
        toolbar.title = getString(R.string.profile_card_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun bindQrCard() {
        val profile = repository.getProfile()
        if (profile == null) {
            Snackbar.make(rootView, R.string.profile_qr_generate_failed, Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        val json = runCatching { repository.buildPublicProfileJson() }.getOrElse {
            Snackbar.make(rootView, R.string.profile_qr_generate_failed, Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        findViewById<TextView>(R.id.studyCardNameText).text = profile.nickname
        findViewById<TextView>(R.id.studyCardSchoolText).text =
            ProfileUiFormatter.schoolLine(profile.school, profile.major, profile.grade)
        findViewById<TextView>(R.id.publicScopeText).text = publicScope(profile)
        findViewById<TextView>(R.id.publicJsonText).text = prettyJson(json)
        findViewById<ImageView>(R.id.qrImageView).setImageBitmap(createQrBitmap(json))
    }

    private fun publicScope(profile: UserProfile): String {
        return buildString {
            append(getString(R.string.profile_public_scope_body))
            append("\n\n")
            append("昵称：").append(profile.nickname)
            append("\n学校：").append(ProfileUiFormatter.optional(profile.school))
            append("\n专业：").append(ProfileUiFormatter.optional(profile.major))
            append("\n年级：").append(ProfileUiFormatter.optional(profile.grade))
            append("\nGitHub：").append(ProfileUiFormatter.optional(profile.github))
            append("\n邮箱：").append(if (profile.showEmail) ProfileUiFormatter.optional(profile.email) else getString(R.string.profile_public_contact_hidden))
            append("\n手机号：").append(if (profile.showPhone) ProfileUiFormatter.optional(profile.phone) else getString(R.string.profile_public_contact_hidden))
        }
    }

    private fun prettyJson(json: String): String {
        return runCatching { JSONObject(json).toString(2) }.getOrDefault(json)
    }

    private fun createQrBitmap(content: String): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
        val pixels = IntArray(QR_SIZE * QR_SIZE)
        for (y in 0 until QR_SIZE) {
            for (x in 0 until QR_SIZE) {
                pixels[y * QR_SIZE + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, QR_SIZE, 0, 0, QR_SIZE, QR_SIZE)
        }
    }

    companion object {
        private const val QR_SIZE = 800
    }
}
