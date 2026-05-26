package com.example.campusmate.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.DataMaintenanceRepository
import com.example.campusmate.data.repository.DemoDataRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler
import com.example.campusmate.ui.buddy.BuddyListActivity
import com.example.campusmate.ui.profile.ProfileActivity
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.NotificationUtils
import com.example.campusmate.util.PermissionUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Settings and demo-maintenance entry points backed by SharedPreferences and repositories. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var reminderScheduler: AlarmReminderScheduler
    private lateinit var dataMaintenanceRepository: DataMaintenanceRepository

    private lateinit var rootView: View
    private lateinit var dailyGoalInputLayout: TextInputLayout
    private lateinit var dailyGoalInput: TextInputEditText
    private lateinit var weatherCityInputLayout: TextInputLayout
    private lateinit var weatherCityInput: TextInputEditText
    private lateinit var reminderSwitch: SwitchMaterial
    private lateinit var immersiveSwitch: SwitchMaterial
    private lateinit var mockWeatherSwitch: SwitchMaterial
    private lateinit var notificationStatusText: TextView
    private lateinit var exactAlarmStatusText: TextView
    private lateinit var dndStatusText: TextView

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            refreshPermissionStatus()
            showMessage(
                if (granted) {
                    getString(R.string.settings_notification_permission_granted)
                } else {
                    getString(R.string.settings_notification_permission_denied)
                }
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsRepository = SettingsRepository(requireContext())
        taskRepository = TaskRepository(requireContext())
        reminderScheduler = AlarmReminderScheduler(requireContext())
        dataMaintenanceRepository = DataMaintenanceRepository(requireContext())

        bindViews(view)
        bindCurrentSettings()
        setupActions(view)
        refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::notificationStatusText.isInitialized) {
            refreshPermissionStatus()
        }
    }

    private fun bindViews(view: View) {
        rootView = view.findViewById(R.id.settingsRoot)
        dailyGoalInputLayout = view.findViewById(R.id.dailyGoalInputLayout)
        dailyGoalInput = view.findViewById(R.id.dailyGoalInput)
        weatherCityInputLayout = view.findViewById(R.id.weatherCityInputLayout)
        weatherCityInput = view.findViewById(R.id.weatherCityInput)
        reminderSwitch = view.findViewById(R.id.reminderSwitch)
        immersiveSwitch = view.findViewById(R.id.immersiveSwitch)
        mockWeatherSwitch = view.findViewById(R.id.mockWeatherSwitch)
        notificationStatusText = view.findViewById(R.id.notificationStatusText)
        exactAlarmStatusText = view.findViewById(R.id.exactAlarmStatusText)
        dndStatusText = view.findViewById(R.id.dndStatusText)
    }

    private fun bindCurrentSettings() {
        dailyGoalInput.setText(settingsRepository.getDailyGoalMinutes().toString())
        weatherCityInput.setText(settingsRepository.getWeatherCity())
        reminderSwitch.isChecked = settingsRepository.isReminderEnabled()
        immersiveSwitch.isChecked = settingsRepository.isImmersiveModeEnabled()
        mockWeatherSwitch.isChecked = settingsRepository.isMockWeatherEnabled()
    }

    private fun setupActions(view: View) {
        view.findViewById<MaterialButton>(R.id.saveDailyGoalButton).setOnClickListener {
            saveDailyGoal()
        }
        view.findViewById<MaterialButton>(R.id.saveWeatherCityButton).setOnClickListener {
            saveWeatherCity()
        }
        mockWeatherSwitch.setOnCheckedChangeListener { _, checked ->
            settingsRepository.setMockWeatherEnabled(checked)
            showMessage(
                getString(
                    if (checked) {
                        R.string.settings_weather_mock_enabled_result
                    } else {
                        R.string.settings_weather_remote_enabled_result
                    }
                )
            )
        }
        reminderSwitch.setOnCheckedChangeListener { _, checked ->
            settingsRepository.setReminderEnabled(checked)
            if (checked) {
                val scheduledCount = rescheduleFutureReminders()
                showMessage(getString(R.string.settings_reminder_enabled_result, scheduledCount))
            } else {
                cancelAllTaskReminders()
                showMessage(getString(R.string.settings_reminder_disabled_result))
            }
            refreshPermissionStatus()
        }
        immersiveSwitch.setOnCheckedChangeListener { _, checked ->
            settingsRepository.setImmersiveModeEnabled(checked)
            showMessage(
                getString(
                    if (checked) {
                        R.string.settings_immersive_enabled_result
                    } else {
                        R.string.settings_immersive_disabled_result
                    }
                )
            )
        }
        view.findViewById<MaterialButton>(R.id.notificationPermissionButton).setOnClickListener {
            requestOrOpenNotificationSettings()
        }
        view.findViewById<MaterialButton>(R.id.exactAlarmButton).setOnClickListener {
            openSystemSettings(PermissionUtils.exactAlarmSettingsIntent(requireContext()))
        }
        view.findViewById<MaterialButton>(R.id.dndSettingsButton).setOnClickListener {
            openSystemSettings(PermissionUtils.notificationPolicyAccessSettingsIntent())
        }
        view.findViewById<MaterialButton>(R.id.profileEntryButton).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.buddyEntryButton).setOnClickListener {
            startActivity(Intent(requireContext(), BuddyListActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.seedDemoDataButton).setOnClickListener {
            confirmSeedDemoData()
        }
        view.findViewById<MaterialButton>(R.id.clearDataButton).setOnClickListener {
            confirmClearData()
        }
    }

    private fun saveDailyGoal() {
        val value = dailyGoalInput.text?.toString()?.toIntOrNull()
        if (value == null || value !in MIN_DAILY_GOAL_MINUTES..MAX_DAILY_GOAL_MINUTES) {
            dailyGoalInputLayout.error = getString(R.string.settings_daily_goal_invalid)
            return
        }
        dailyGoalInputLayout.error = null
        settingsRepository.setDailyGoalMinutes(value)
        showMessage(getString(R.string.settings_daily_goal_saved, value))
    }

    private fun saveWeatherCity() {
        val city = weatherCityInput.text?.toString()?.trim().orEmpty()
        if (city.isBlank()) {
            weatherCityInputLayout.error = getString(R.string.settings_weather_city_invalid)
            return
        }
        weatherCityInputLayout.error = null
        settingsRepository.setWeatherCity(city)
        showMessage(getString(R.string.settings_weather_city_saved, city))
    }

    private fun requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasPostNotificationsPermission(requireContext())
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        openSystemSettings(PermissionUtils.appNotificationSettingsIntent(requireContext()))
    }

    private fun confirmSeedDemoData() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_seed_demo_confirm_title)
            .setMessage(R.string.settings_seed_demo_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.settings_seed_demo_data) { _, _ ->
                resetAndSeedDemoData()
            }
            .show()
    }

    private fun resetAndSeedDemoData() {
        cancelAllTaskReminders()
        dataMaintenanceRepository.clearAllData()
        val result = DemoDataRepository(requireContext()).seedPresentationDemoData()
        val scheduledCount = rescheduleFutureReminders()
        refreshPermissionStatus()
        showMessage(
            getString(
                R.string.settings_seed_demo_result,
                result.courseCount,
                result.taskCount,
                result.recordCount,
                scheduledCount
            )
        )
    }

    private fun confirmClearData() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_clear_data_confirm_title)
            .setMessage(R.string.settings_clear_data_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.settings_clear_data) { _, _ ->
                clearData()
            }
            .show()
    }

    private fun clearData() {
        cancelAllTaskReminders()
        val result = dataMaintenanceRepository.clearAllData()
        showMessage(getString(R.string.settings_clear_data_result, result.totalCount))
    }

    private fun rescheduleFutureReminders(): Int {
        if (!settingsRepository.isReminderEnabled()) return 0
        if (!PermissionUtils.hasPostNotificationsPermission(requireContext()) ||
            !NotificationUtils.areNotificationsEnabled(requireContext())
        ) {
            return 0
        }

        val now = DateTimeUtils.nowMillis()
        return taskRepository.getAllTasks()
            .filter { task ->
                task.status == StudyTask.STATUS_TODO &&
                    !task.isDeleted &&
                    task.remindAt != null &&
                    task.remindAt > now
            }
            .count { task -> reminderScheduler.scheduleTaskReminder(task).scheduled }
    }

    private fun cancelAllTaskReminders() {
        taskRepository.getAllTasks().forEach { task ->
            reminderScheduler.cancelTaskReminder(task.id)
        }
    }

    private fun refreshPermissionStatus() {
        val notificationReady = PermissionUtils.hasPostNotificationsPermission(requireContext()) &&
            NotificationUtils.areNotificationsEnabled(requireContext())
        notificationStatusText.text = getString(
            if (notificationReady) {
                R.string.settings_notification_status_enabled
            } else {
                R.string.settings_notification_status_disabled
            }
        )

        exactAlarmStatusText.text = getString(
            if (PermissionUtils.canScheduleExactAlarms(requireContext())) {
                R.string.settings_exact_alarm_status_enabled
            } else {
                R.string.settings_exact_alarm_status_fallback
            }
        )

        dndStatusText.text = getString(
            if (PermissionUtils.hasNotificationPolicyAccess(requireContext())) {
                R.string.settings_dnd_status_enabled
            } else {
                R.string.settings_dnd_status_disabled
            }
        )
    }

    private fun openSystemSettings(intent: Intent?) {
        if (intent == null) {
            showMessage(getString(R.string.settings_system_entry_unavailable))
            return
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(getString(R.string.settings_system_entry_unavailable))
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val MIN_DAILY_GOAL_MINUTES = 1
        private const val MAX_DAILY_GOAL_MINUTES = 1440
    }
}
