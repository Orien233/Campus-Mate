package com.example.campusmate.ui.profile

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.UserProfile
import com.example.campusmate.data.repository.UserProfileRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Edits the local profile used by QR and future NFC study-card sharing. */
class EditProfileActivity : AppCompatActivity() {
    private lateinit var repository: UserProfileRepository
    private lateinit var rootView: View
    private lateinit var nicknameLayout: TextInputLayout
    private var currentProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
        repository = UserProfileRepository(this)
        rootView = findViewById(R.id.editProfileRoot)
        nicknameLayout = findViewById(R.id.nicknameInputLayout)

        setupToolbar()
        bindCurrentProfile()
        findViewById<MaterialButton>(R.id.saveProfileButton).setOnClickListener {
            saveProfile()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.editProfileToolbar)
        toolbar.title = getString(R.string.profile_edit_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun bindCurrentProfile() {
        val profile = repository.getProfile()
        currentProfile = profile
        if (profile == null) return
        findViewById<TextInputEditText>(R.id.nicknameInput).setText(profile.nickname)
        findViewById<TextInputEditText>(R.id.schoolInput).setText(profile.school)
        findViewById<TextInputEditText>(R.id.majorInput).setText(profile.major)
        findViewById<TextInputEditText>(R.id.gradeInput).setText(profile.grade)
        findViewById<TextInputEditText>(R.id.bioInput).setText(profile.bio)
        findViewById<TextInputEditText>(R.id.githubInput).setText(profile.github)
        findViewById<TextInputEditText>(R.id.emailInput).setText(profile.email)
        findViewById<TextInputEditText>(R.id.phoneInput).setText(profile.phone)
        findViewById<SwitchMaterial>(R.id.showEmailSwitch).isChecked = profile.showEmail
        findViewById<SwitchMaterial>(R.id.showPhoneSwitch).isChecked = profile.showPhone
    }

    private fun saveProfile() {
        val nickname = inputText(R.id.nicknameInput)
        if (nickname.isBlank()) {
            nicknameLayout.error = getString(R.string.profile_nickname_required)
            return
        }
        nicknameLayout.error = null

        val profile = UserProfile(
            id = currentProfile?.id ?: 0L,
            nickname = nickname,
            school = inputTextOrNull(R.id.schoolInput),
            major = inputTextOrNull(R.id.majorInput),
            grade = inputTextOrNull(R.id.gradeInput),
            bio = inputTextOrNull(R.id.bioInput),
            avatarUri = null,
            github = inputTextOrNull(R.id.githubInput),
            email = inputTextOrNull(R.id.emailInput),
            phone = inputTextOrNull(R.id.phoneInput),
            showEmail = findViewById<SwitchMaterial>(R.id.showEmailSwitch).isChecked,
            showPhone = findViewById<SwitchMaterial>(R.id.showPhoneSwitch).isChecked,
            createdAt = currentProfile?.createdAt ?: 0L,
            updatedAt = currentProfile?.updatedAt ?: 0L
        )
        val profileId = runCatching { repository.saveProfile(profile) }.getOrDefault(-1L)
        if (profileId > 0L) {
            Snackbar.make(rootView, R.string.profile_saved, Snackbar.LENGTH_SHORT).show()
            finish()
        } else {
            Snackbar.make(rootView, R.string.profile_save_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun inputText(id: Int): String {
        return findViewById<TextInputEditText>(id).text?.toString()?.trim().orEmpty()
    }

    private fun inputTextOrNull(id: Int): String? = inputText(id).takeIf { it.isNotEmpty() }
}
