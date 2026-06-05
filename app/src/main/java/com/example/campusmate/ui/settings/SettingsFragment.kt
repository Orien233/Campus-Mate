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
import com.example.campusmate.domain.weather.WeatherLocationResolver
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
    private lateinit var llmSettingsUiBinder: LlmSettingsUiBinder

    private lateinit var rootView: View
    private lateinit var dailyGoalInputLayout: TextInputLayout
    private lateinit var dailyGoalInput: TextInputEditText
    private lateinit var planEarliestTimeInputLayout: TextInputLayout
    private lateinit var planEarliestTimeInput: TextInputEditText
    private lateinit var planLatestTimeInputLayout: TextInputLayout
    private lateinit var planLatestTimeInput: TextInputEditText
    private lateinit var weatherCityInputLayout: TextInputLayout
    private lateinit var weatherCityInput: TextInputEditText
    private lateinit var reminderSwitch: SwitchMaterial
    private lateinit var immersiveSwitch: SwitchMaterial
    private lateinit var focusDndSwitch: SwitchMaterial
    private lateinit var notificationFilterSwitch: SwitchMaterial
    private lateinit var weatherLocationStatusText: TextView
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

    private val weatherLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            settingsRepository.setWeatherLocationGuideShown(true)
            if (granted) {
                updateWeatherCityFromLocation()
            } else {
                refreshWeatherLocationStatus()
                showMessage(getString(R.string.settings_weather_location_permission_denied))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsRepository = SettingsRepository(requireContext())
        taskRepository = TaskRepository(requireContext())
        reminderScheduler = AlarmReminderScheduler(requireContext())
        dataMaintenanceRepository = DataMaintenanceRepository(requireContext())

        bindViews(view)
        llmSettingsUiBinder = LlmSettingsUiBinder(this, view, ::showMessage)
        llmSettingsUiBinder.bind()
        bindCurrentSettings()
        setupActions(view)
        refreshPermissionStatus()
        applySectionVisibility(view)
    }

    override fun onResume() {
        super.onResume()
        if (::notificationStatusText.isInitialized) {
            refreshPermissionStatus()
            refreshWeatherLocationStatus()
        }
    }

    override fun onDestroyView() {
        if (::llmSettingsUiBinder.isInitialized) {
            llmSettingsUiBinder.shutdown()
        }
        super.onDestroyView()
    }

    private fun bindViews(view: View) {
        rootView = view.findViewById(R.id.settingsRoot)
        dailyGoalInputLayout = view.findViewById(R.id.dailyGoalInputLayout)
        dailyGoalInput = view.findViewById(R.id.dailyGoalInput)
        planEarliestTimeInputLayout = view.findViewById(R.id.planEarliestTimeInputLayout)
        planEarliestTimeInput = view.findViewById(R.id.planEarliestTimeInput)
        planLatestTimeInputLayout = view.findViewById(R.id.planLatestTimeInputLayout)
        planLatestTimeInput = view.findViewById(R.id.planLatestTimeInput)
        weatherCityInputLayout = view.findViewById(R.id.weatherCityInputLayout)
        weatherCityInput = view.findViewById(R.id.weatherCityInput)
        reminderSwitch = view.findViewById(R.id.reminderSwitch)
        immersiveSwitch = view.findViewById(R.id.immersiveSwitch)
        focusDndSwitch = view.findViewById(R.id.focusDndSwitch)
        notificationFilterSwitch = view.findViewById(R.id.notificationFilterSwitch)
        weatherLocationStatusText = view.findViewById(R.id.weatherLocationStatusText)
        notificationStatusText = view.findViewById(R.id.notificationStatusText)
        exactAlarmStatusText = view.findViewById(R.id.exactAlarmStatusText)
        dndStatusText = view.findViewById(R.id.dndStatusText)
    }

    private fun bindCurrentSettings() {
        dailyGoalInput.setText(settingsRepository.getDailyGoalMinutes().toString())
        planEarliestTimeInput.setText(settingsRepository.getPlanEarliestTime())
        planLatestTimeInput.setText(settingsRepository.getPlanLatestTime())
        weatherCityInput.setText(settingsRepository.getWeatherCity())
        reminderSwitch.isChecked = settingsRepository.isReminderEnabled()
        immersiveSwitch.isChecked = settingsRepository.isImmersiveModeEnabled()
        focusDndSwitch.isChecked = settingsRepository.isFocusDndEnabled()
        notificationFilterSwitch.isChecked = settingsRepository.isNotificationFilterEnabled()
        refreshWeatherLocationStatus()
    }

    private fun setupActions(view: View) {
        view.findViewById<MaterialButton>(R.id.openAiSettingsButton).setOnClickListener {
            startActivity(SettingsSectionActivity.intentFor(requireContext(), SECTION_AI))
        }
        view.findViewById<MaterialButton>(R.id.openFeatureSettingsButton).setOnClickListener {
            startActivity(SettingsSectionActivity.intentFor(requireContext(), SECTION_FEATURES))
        }
        view.findViewById<MaterialButton>(R.id.openBuddySettingsButton).setOnClickListener {
            startActivity(SettingsSectionActivity.intentFor(requireContext(), SECTION_BUDDY))
        }
        view.findViewById<MaterialButton>(R.id.saveDailyGoalButton).setOnClickListener {
            saveDailyGoal()
        }
        view.findViewById<MaterialButton>(R.id.savePlanTimeWindowButton).setOnClickListener {
            savePlanTimeWindow()
        }
        view.findViewById<MaterialButton>(R.id.saveWeatherCityButton).setOnClickListener {
            saveWeatherCity()
        }
        view.findViewById<MaterialButton>(R.id.useCurrentWeatherLocationButton).setOnClickListener {
            requestWeatherLocation()
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
        focusDndSwitch.setOnCheckedChangeListener { _, checked ->
            settingsRepository.setFocusDndEnabled(checked)
            showMessage(
                getString(
                    if (checked) {
                        R.string.focus_dnd_enabled
                    } else {
                        R.string.focus_dnd_disabled
                    }
                )
            )
        }
        notificationFilterSwitch.setOnCheckedChangeListener { _, checked ->
            settingsRepository.setNotificationFilterEnabled(checked)
            showMessage(
                getString(
                    if (checked) {
                        R.string.notification_filter_enabled
                    } else {
                        R.string.notification_filter_disabled
                    }
                )
            )
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

    private fun savePlanTimeWindow() {
        val earliest = planEarliestTimeInput.text?.toString()?.trim().orEmpty()
        val latest = planLatestTimeInput.text?.toString()?.trim().orEmpty()
        val earliestMinutes = parseTimeMinutes(earliest)
        val latestMinutes = parseTimeMinutes(latest)
        planEarliestTimeInputLayout.error = null
        planLatestTimeInputLayout.error = null
        if (earliestMinutes == null) {
            planEarliestTimeInputLayout.error = getString(R.string.settings_plan_time_invalid)
            return
        }
        if (latestMinutes == null) {
            planLatestTimeInputLayout.error = getString(R.string.settings_plan_time_invalid)
            return
        }
        if (earliestMinutes >= latestMinutes) {
            planLatestTimeInputLayout.error = getString(R.string.settings_plan_time_range_invalid)
            return
        }
        settingsRepository.setPlanEarliestTime(earliest)
        settingsRepository.setPlanLatestTime(latest)
        planEarliestTimeInput.setText(settingsRepository.getPlanEarliestTime())
        planLatestTimeInput.setText(settingsRepository.getPlanLatestTime())
        showMessage(getString(R.string.settings_plan_time_window_saved))
    }

    private fun saveWeatherCity() {
        val city = weatherCityInput.text?.toString()?.trim().orEmpty()
        if (city.isBlank()) {
            weatherCityInputLayout.error = getString(R.string.settings_weather_city_invalid)
            return
        }
        weatherCityInputLayout.error = null
        settingsRepository.setWeatherCity(city)
        refreshWeatherLocationStatus()
        showMessage(getString(R.string.settings_weather_city_saved, city))
    }

    private fun requestWeatherLocation() {
        if (!PermissionUtils.hasCoarseLocationPermission(requireContext())) {
            settingsRepository.setWeatherLocationGuideShown(true)
            weatherLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        updateWeatherCityFromLocation()
    }

    private fun updateWeatherCityFromLocation() {
        val appContext = requireContext().applicationContext
        weatherLocationStatusText.text = getString(R.string.settings_weather_location_resolving)
        Thread {
            val city = WeatherLocationResolver(appContext).resolveCity()
            val targetView = view ?: return@Thread
            targetView.post {
                if (city.isNullOrBlank()) {
                    refreshWeatherLocationStatus()
                    showMessage(getString(R.string.settings_weather_location_failed))
                    return@post
                }
                weatherCityInput.setText(city)
                settingsRepository.setWeatherCityFromLocation(city)
                refreshWeatherLocationStatus()
                showMessage(getString(R.string.settings_weather_location_saved, city))
            }
        }.start()
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

    private fun refreshWeatherLocationStatus() {
        if (!::weatherLocationStatusText.isInitialized) return
        val updatedAt = settingsRepository.getWeatherCityUpdatedAt()
        weatherLocationStatusText.text = if (PermissionUtils.hasCoarseLocationPermission(requireContext())) {
            if (updatedAt > 0L) {
                getString(R.string.settings_weather_location_status_enabled, DateTimeUtils.formatDateTime(updatedAt))
            } else {
                getString(R.string.settings_weather_location_status_ready)
            }
        } else {
            getString(R.string.settings_weather_location_status_disabled)
        }
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

    private fun applySectionVisibility(view: View) {
        val section = arguments?.getString(ARG_SECTION)
        val homeCard = view.findViewById<View>(R.id.settingsHomeCard)
        val featureCards = listOf(
            R.id.settingsGoalCard,
            R.id.settingsPreferencesCard,
            R.id.settingsWeatherCard,
            R.id.settingsPermissionsCard,
            R.id.settingsDemoCard
        ).map { view.findViewById<View>(it) }
        val aiCards = listOf(R.id.settingsLlmCard).map { view.findViewById<View>(it) }
        val buddyCards = listOf(R.id.settingsProfileCard).map { view.findViewById<View>(it) }
        val allSectionCards = featureCards + aiCards + buddyCards

        if (section == null) {
            homeCard.visibility = View.VISIBLE
            allSectionCards.forEach { it.visibility = View.GONE }
            return
        }

        homeCard.visibility = View.GONE
        featureCards.forEach { it.visibility = if (section == SECTION_FEATURES) View.VISIBLE else View.GONE }
        aiCards.forEach { it.visibility = if (section == SECTION_AI) View.VISIBLE else View.GONE }
        buddyCards.forEach { it.visibility = if (section == SECTION_BUDDY) View.VISIBLE else View.GONE }
    }

    private fun parseTimeMinutes(value: String): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    companion object {
        const val SECTION_AI = "ai"
        const val SECTION_FEATURES = "features"
        const val SECTION_BUDDY = "buddy"
        private const val ARG_SECTION = "arg_section"
        private const val MIN_DAILY_GOAL_MINUTES = 1
        private const val MAX_DAILY_GOAL_MINUTES = 1440

        fun newSectionInstance(section: String): SettingsFragment {
            return SettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SECTION, section)
                }
            }
        }
    }
}
