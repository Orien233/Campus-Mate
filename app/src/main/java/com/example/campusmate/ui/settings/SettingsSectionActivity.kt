package com.example.campusmate.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.google.android.material.appbar.MaterialToolbar

class SettingsSectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)
        val section = intent.getStringExtra(EXTRA_SECTION) ?: SettingsFragment.SECTION_FEATURES
        val toolbar = findViewById<MaterialToolbar>(R.id.settingsSectionToolbar)
        toolbar.title = titleForSection(section)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsSectionContainer, SettingsFragment.newSectionInstance(section))
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun titleForSection(section: String): String {
        return when (section) {
            SettingsFragment.SECTION_AI -> getString(R.string.settings_section_ai)
            SettingsFragment.SECTION_BUDDY -> getString(R.string.settings_section_buddy)
            else -> getString(R.string.settings_section_features)
        }
    }

    companion object {
        const val EXTRA_SECTION = "extra_section"

        fun intentFor(context: android.content.Context, section: String): Intent {
            return Intent(context, SettingsSectionActivity::class.java)
                .putExtra(EXTRA_SECTION, section)
        }
    }
}
