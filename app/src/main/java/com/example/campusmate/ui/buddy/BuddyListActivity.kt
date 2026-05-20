package com.example.campusmate.ui.buddy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.repository.StudyBuddyRepository
import com.example.campusmate.ui.profile.ScanQrActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** Lists confirmed study buddies and links to QR scanning. */
class BuddyListActivity : AppCompatActivity() {
    private lateinit var repository: StudyBuddyRepository
    private lateinit var adapter: BuddyAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buddy_list)
        repository = StudyBuddyRepository(this)

        setupToolbar()
        setupList()
        findViewById<FloatingActionButton>(R.id.scanBuddyFab).setOnClickListener {
            startActivity(Intent(this, ScanQrActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadBuddies()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.buddyListToolbar)
        toolbar.title = getString(R.string.buddy_list_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupList() {
        emptyText = findViewById(R.id.buddyEmptyText)
        emptyText.text = "${getString(R.string.buddy_empty_title)}\n${getString(R.string.buddy_empty_body)}"
        recyclerView = findViewById(R.id.buddyRecyclerView)
        adapter = BuddyAdapter { buddy ->
            startActivity(Intent(this, BuddyDetailActivity::class.java).putExtra(BuddyDetailActivity.EXTRA_BUDDY_ID, buddy.id))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadBuddies() {
        val buddies = repository.getAllBuddies()
        adapter.submitList(buddies)
        emptyText.visibility = if (buddies.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (buddies.isEmpty()) View.GONE else View.VISIBLE
    }
}
