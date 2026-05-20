package com.example.campusmate.ui.buddy

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.data.repository.StudyBuddyRepository
import com.example.campusmate.ui.profile.ProfileUiFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Read-only detail screen for a saved study buddy. */
class BuddyDetailActivity : AppCompatActivity() {
    private lateinit var repository: StudyBuddyRepository
    private lateinit var rootView: View
    private var buddyId: Long = 0L
    private var currentBuddy: StudyBuddy? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buddy_detail)
        repository = StudyBuddyRepository(this)
        rootView = findViewById(R.id.buddyDetailRoot)
        buddyId = intent.getLongExtra(EXTRA_BUDDY_ID, 0L)

        setupToolbar()
        findViewById<MaterialButton>(R.id.deleteBuddyButton).setOnClickListener {
            currentBuddy?.let { confirmDelete(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadBuddy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.buddyDetailToolbar)
        toolbar.title = getString(R.string.buddy_detail_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadBuddy() {
        val buddy = repository.getBuddyById(buddyId)
        if (buddy == null) {
            Snackbar.make(rootView, R.string.buddy_not_found, Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        currentBuddy = buddy
        bindBuddy(buddy)
    }

    private fun bindBuddy(buddy: StudyBuddy) {
        findViewById<TextView>(R.id.buddyDetailNameText).text = buddy.nickname
        findViewById<TextView>(R.id.buddyDetailSchoolText).text =
            ProfileUiFormatter.schoolLine(buddy.school, buddy.major, buddy.grade)
        findViewById<TextView>(R.id.buddyDetailBioText).text = ProfileUiFormatter.optional(buddy.bio)
        findViewById<TextView>(R.id.buddyDetailGithubText).text = "GitHub：${ProfileUiFormatter.optional(buddy.github)}"
        findViewById<TextView>(R.id.buddyDetailEmailText).text = "邮箱：${ProfileUiFormatter.optional(buddy.email)}"
        findViewById<TextView>(R.id.buddyDetailPhoneText).text = "手机号：${ProfileUiFormatter.optional(buddy.phone)}"
        val source = getString(R.string.buddy_added_from, ProfileUiFormatter.sourceLabel(this, buddy.source))
        findViewById<TextView>(R.id.buddyDetailSourceText).text =
            "$source\n${ProfileUiFormatter.addedAtLabel(this, buddy.addedAt)}"
    }

    private fun confirmDelete(buddy: StudyBuddy) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.buddy_delete_title)
            .setMessage(getString(R.string.buddy_delete_message, buddy.nickname))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (repository.deleteBuddy(buddy.id)) {
                    Snackbar.make(rootView, R.string.buddy_delete_success, Snackbar.LENGTH_SHORT).show()
                    finish()
                } else {
                    Snackbar.make(rootView, R.string.buddy_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_BUDDY_ID = "extra_buddy_id"
    }
}
