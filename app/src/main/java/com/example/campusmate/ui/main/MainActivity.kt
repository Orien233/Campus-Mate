package com.example.campusmate.ui.main

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.ui.course.CourseListFragment
import com.example.campusmate.ui.dashboard.DashboardFragment
import com.example.campusmate.ui.plan.PlanListFragment
import com.example.campusmate.ui.settings.SettingsFragment
import com.example.campusmate.ui.statistics.StatisticsFragment
import com.example.campusmate.ui.task.TaskListFragment
import com.example.campusmate.util.SystemBarsInsets
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Hosts the top-level navigation shell and preserves primary fragments. */
class MainActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView
    private var selectedItemId: Int = R.id.nav_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.topAppBar)
        bottomNavigationView = findViewById(R.id.bottomNavigation)
        setSupportActionBar(toolbar)
        SystemBarsInsets.apply(this)

        selectedItemId = savedInstanceState?.getInt(KEY_SELECTED_ITEM)
            ?: intent.getIntExtra(EXTRA_START_DESTINATION, R.id.nav_dashboard)
        bottomNavigationView.setOnItemSelectedListener { item ->
            showFragment(item.itemId)
            true
        }
        bottomNavigationView.setOnItemReselectedListener { item ->
            showFragment(item.itemId)
        }

        if (!isBottomNavigationItem(selectedItemId)) {
            showFragment(selectedItemId)
        } else if (bottomNavigationView.selectedItemId != selectedItemId) {
            bottomNavigationView.selectedItemId = selectedItemId
        } else {
            showFragment(selectedItemId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_settings -> {
                showFragment(R.id.nav_settings)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_ITEM, selectedItemId)
        super.onSaveInstanceState(outState)
    }

    fun navigateTo(itemId: Int) {
        if (isBottomNavigationItem(itemId) && bottomNavigationView.selectedItemId != itemId) {
            bottomNavigationView.selectedItemId = itemId
        } else {
            showFragment(itemId)
        }
    }

    private fun showFragment(itemId: Int) {
        val tag = fragmentTag(itemId)
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.primaryNavigationFragment
        val targetFragment = fragmentManager.findFragmentByTag(tag) ?: createFragment(itemId)

        fragmentManager.beginTransaction().apply {
            currentFragment?.let { hide(it) }
            if (targetFragment.isAdded) {
                show(targetFragment)
            } else {
                add(R.id.fragmentContainer, targetFragment, tag)
            }
            setPrimaryNavigationFragment(targetFragment)
            commit()
        }

        selectedItemId = itemId
        toolbar.title = getString(titleRes(itemId))
    }

    private fun createFragment(itemId: Int): Fragment {
        return when (itemId) {
            R.id.nav_courses -> CourseListFragment()
            R.id.nav_tasks -> TaskListFragment()
            R.id.nav_plan -> PlanListFragment()
            R.id.nav_statistics -> StatisticsFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> DashboardFragment()
        }
    }

    private fun fragmentTag(itemId: Int): String {
        return when (itemId) {
            R.id.nav_courses -> TAG_COURSES
            R.id.nav_tasks -> TAG_TASKS
            R.id.nav_plan -> TAG_PLAN
            R.id.nav_statistics -> TAG_STATISTICS
            R.id.nav_settings -> TAG_SETTINGS
            else -> TAG_DASHBOARD
        }
    }

    private fun titleRes(itemId: Int): Int {
        return when (itemId) {
            R.id.nav_courses -> R.string.nav_courses
            R.id.nav_tasks -> R.string.nav_tasks
            R.id.nav_plan -> R.string.nav_plan
            R.id.nav_statistics -> R.string.nav_statistics
            R.id.nav_settings -> R.string.nav_settings
            else -> R.string.app_name
        }
    }

    private fun isBottomNavigationItem(itemId: Int): Boolean {
        return bottomNavigationView.menu.findItem(itemId) != null
    }

    companion object {
        private const val KEY_SELECTED_ITEM = "selected_item"
        const val EXTRA_START_DESTINATION = "extra_start_destination"
        private const val TAG_DASHBOARD = "dashboard"
        private const val TAG_COURSES = "courses"
        private const val TAG_TASKS = "tasks"
        private const val TAG_PLAN = "plan"
        private const val TAG_STATISTICS = "statistics"
        private const val TAG_SETTINGS = "settings"
    }
}
