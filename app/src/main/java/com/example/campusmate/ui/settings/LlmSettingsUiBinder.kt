package com.example.campusmate.ui.settings

import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.llm.LlmAuthHeaderType
import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmProviderPreset
import com.example.campusmate.data.model.llm.LlmProviderType
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.llm.LlmProviderPresets
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LlmSettingsUiBinder(
    private val fragment: Fragment,
    private val rootView: View,
    private val showMessage: (String) -> Unit
) {
    private val repository = LlmSettingsRepository(fragment.requireContext())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var enabledSwitch: SwitchMaterial
    private lateinit var scheduleParseSwitch: SwitchMaterial
    private lateinit var taskParseSwitch: SwitchMaterial
    private lateinit var planGenerateSwitch: SwitchMaterial
    private lateinit var providerInput: MaterialAutoCompleteTextView
    private lateinit var baseUrlInputLayout: TextInputLayout
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var modelInputLayout: TextInputLayout
    private lateinit var modelInput: TextInputEditText
    private lateinit var authHeaderInput: MaterialAutoCompleteTextView
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiKeyStatusText: TextView
    private lateinit var temperatureInputLayout: TextInputLayout
    private lateinit var temperatureInput: TextInputEditText
    private lateinit var timeoutInputLayout: TextInputLayout
    private lateinit var timeoutInput: TextInputEditText
    private lateinit var maxTokensInputLayout: TextInputLayout
    private lateinit var maxTokensInput: TextInputEditText
    private lateinit var presetNotesText: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var testButton: MaterialButton
    private lateinit var clearKeyButton: MaterialButton
    private lateinit var restorePresetButton: MaterialButton
    private lateinit var advancedToggleButton: MaterialButton
    private lateinit var advancedSettingsContainer: View

    private var selectedPreset: LlmProviderPreset = LlmProviderPresets.default
    private var advancedExpanded: Boolean = false

    fun bind() {
        bindViews()
        setupProviderDropdown()
        setupAuthHeaderDropdown()
        bindCurrentSettings()
        setupActions()
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun bindViews() {
        enabledSwitch = rootView.findViewById(R.id.llmEnabledSwitch)
        scheduleParseSwitch = rootView.findViewById(R.id.llmScheduleParseSwitch)
        taskParseSwitch = rootView.findViewById(R.id.llmTaskParseSwitch)
        planGenerateSwitch = rootView.findViewById(R.id.llmPlanGenerateSwitch)
        providerInput = rootView.findViewById(R.id.llmProviderInput)
        baseUrlInputLayout = rootView.findViewById(R.id.llmBaseUrlInputLayout)
        baseUrlInput = rootView.findViewById(R.id.llmBaseUrlInput)
        modelInputLayout = rootView.findViewById(R.id.llmModelInputLayout)
        modelInput = rootView.findViewById(R.id.llmModelInput)
        authHeaderInput = rootView.findViewById(R.id.llmAuthHeaderInput)
        apiKeyInput = rootView.findViewById(R.id.llmApiKeyInput)
        apiKeyStatusText = rootView.findViewById(R.id.llmApiKeyStatusText)
        temperatureInputLayout = rootView.findViewById(R.id.llmTemperatureInputLayout)
        temperatureInput = rootView.findViewById(R.id.llmTemperatureInput)
        timeoutInputLayout = rootView.findViewById(R.id.llmTimeoutInputLayout)
        timeoutInput = rootView.findViewById(R.id.llmTimeoutInput)
        maxTokensInputLayout = rootView.findViewById(R.id.llmMaxTokensInputLayout)
        maxTokensInput = rootView.findViewById(R.id.llmMaxTokensInput)
        presetNotesText = rootView.findViewById(R.id.llmPresetNotesText)
        saveButton = rootView.findViewById(R.id.llmSaveButton)
        testButton = rootView.findViewById(R.id.llmTestButton)
        clearKeyButton = rootView.findViewById(R.id.llmClearKeyButton)
        restorePresetButton = rootView.findViewById(R.id.llmRestorePresetButton)
        advancedToggleButton = rootView.findViewById(R.id.llmAdvancedToggleButton)
        advancedSettingsContainer = rootView.findViewById(R.id.llmAdvancedSettingsContainer)
    }

    private fun setupProviderDropdown() {
        providerInput.setAdapter(
            ArrayAdapter(
                fragment.requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                LlmProviderPresets.all.map { it.displayName }
            )
        )
        providerInput.setOnItemClickListener { _, _, position, _ ->
            applyPreset(LlmProviderPresets.all[position])
        }
    }

    private fun setupAuthHeaderDropdown() {
        authHeaderInput.setAdapter(
            ArrayAdapter(
                fragment.requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                authHeaderOptions().map { it.first }
            )
        )
        authHeaderInput.setOnClickListener {
            authHeaderInput.showDropDown()
        }
    }

    private fun bindCurrentSettings() {
        val config = repository.getConfig()
        selectedPreset = LlmProviderPresets.findById(config.providerPresetId) ?: LlmProviderPresets.default
        enabledSwitch.isChecked = config.enabled
        scheduleParseSwitch.isChecked = config.scheduleParseEnabled
        taskParseSwitch.isChecked = config.taskParseEnabled
        planGenerateSwitch.isChecked = config.planGenerateEnabled
        providerInput.setText(selectedPreset.displayName, false)
        baseUrlInput.setText(config.baseUrl)
        modelInput.setText(config.model)
        authHeaderInput.setText(authHeaderLabel(config.authHeaderType), false)
        temperatureInput.setText(config.temperature.toString())
        timeoutInput.setText(config.timeoutMillis.toString())
        maxTokensInput.setText(config.maxOutputTokens.toString())
        presetNotesText.text = selectedPreset.notes
        bindApiKeyStatus()
    }

    private fun setupActions() {
        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            val value = apiKeyInput.text?.toString().orEmpty()
            if (hasFocus && LlmSettingsRepository.hasMaskedShape(value)) {
                apiKeyInput.setText("")
            } else if (!hasFocus && value.isBlank() && repository.hasApiKey()) {
                apiKeyInput.setText(repository.getMaskedApiKey())
            }
        }
        saveButton.setOnClickListener { saveSettings() }
        testButton.setOnClickListener { testConnection() }
        clearKeyButton.setOnClickListener {
            repository.clearApiKey()
            apiKeyInput.setText("")
            bindApiKeyStatus()
            showMessage(fragment.getString(R.string.settings_llm_api_key_cleared))
        }
        restorePresetButton.setOnClickListener {
            applyPreset(selectedPreset)
            showMessage(fragment.getString(R.string.settings_llm_preset_restored, selectedPreset.displayName))
        }
        advancedToggleButton.setOnClickListener {
            advancedExpanded = !advancedExpanded
            advancedSettingsContainer.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
            advancedToggleButton.text = fragment.getString(
                if (advancedExpanded) {
                    R.string.settings_llm_advanced_hide
                } else {
                    R.string.settings_llm_advanced_toggle
                }
            )
        }
    }

    private fun applyPreset(preset: LlmProviderPreset) {
        selectedPreset = preset
        providerInput.setText(preset.displayName, false)
        baseUrlInput.setText(preset.baseUrl)
        modelInput.setText(preset.defaultModel)
        authHeaderInput.setText(authHeaderLabel(preset.authHeaderType), false)
        presetNotesText.text = preset.notes
        clearInputErrors()
    }

    private fun saveSettings() {
        val config = readConfigFromInputs(requireEndpoint = false) ?: return
        repository.saveConfig(config)
        currentTypedApiKey()?.let(repository::saveApiKey)
        bindApiKeyStatus()
        showMessage(fragment.getString(R.string.settings_llm_saved))
    }

    private fun testConnection() {
        val config = readConfigFromInputs(requireEndpoint = true) ?: return
        val apiKey = currentTypedApiKey() ?: repository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            showMessage(fragment.getString(R.string.settings_llm_api_key_required))
            return
        }

        testButton.isEnabled = false
        testButton.text = fragment.getString(R.string.settings_llm_testing)
        executor.execute {
            val result = LlmClientFactory.create(config).testConnection(config, apiKey)
            rootView.post {
                if (!fragment.isAdded) return@post
                testButton.isEnabled = true
                testButton.text = fragment.getString(R.string.settings_llm_test_connection)
                when (result) {
                    is LlmGenerateResult.Success -> showMessage(
                        fragment.getString(
                            R.string.settings_llm_test_success,
                            result.providerName,
                            result.model
                        )
                    )
                    is LlmGenerateResult.Failure -> showMessage(result.message)
                }
            }
        }
    }

    private fun readConfigFromInputs(requireEndpoint: Boolean): LlmProviderConfig? {
        clearInputErrors()
        val preset = resolveSelectedPreset()
        selectedPreset = preset
        val baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty()
        val model = modelInput.text?.toString()?.trim().orEmpty()
        val providerType = if (preset.id == LlmProviderPresets.ID_CUSTOM) {
            LlmProviderType.CUSTOM_OPENAI_COMPATIBLE
        } else {
            preset.providerType
        }

        if ((requireEndpoint || providerType == LlmProviderType.CUSTOM_OPENAI_COMPATIBLE) && baseUrl.isBlank()) {
            baseUrlInputLayout.error = fragment.getString(R.string.settings_llm_base_url_required)
            return null
        }
        if ((requireEndpoint || providerType == LlmProviderType.CUSTOM_OPENAI_COMPATIBLE) && model.isBlank()) {
            modelInputLayout.error = fragment.getString(R.string.settings_llm_model_required)
            return null
        }

        val temperature = readFloat(temperatureInput, temperatureInputLayout, 0f, 2f) ?: return null
        val timeoutMillis = readInt(timeoutInput, timeoutInputLayout, 1_000, 120_000) ?: return null
        val maxTokens = readInt(maxTokensInput, maxTokensInputLayout, 1, 32_768) ?: return null

        return LlmProviderConfig(
            enabled = enabledSwitch.isChecked,
            scheduleParseEnabled = scheduleParseSwitch.isChecked,
            taskParseEnabled = taskParseSwitch.isChecked,
            planGenerateEnabled = planGenerateSwitch.isChecked,
            providerPresetId = preset.id,
            providerType = providerType,
            displayName = preset.displayName,
            baseUrl = baseUrl,
            model = model,
            authHeaderType = resolveAuthHeaderType(),
            temperature = temperature,
            timeoutMillis = timeoutMillis,
            maxOutputTokens = maxTokens
        )
    }

    private fun readFloat(
        input: TextInputEditText,
        layout: TextInputLayout,
        min: Float,
        max: Float
    ): Float? {
        val value = input.text?.toString()?.trim()?.toFloatOrNull()
        if (value == null || value !in min..max) {
            layout.error = fragment.getString(R.string.settings_llm_number_invalid)
            return null
        }
        return value
    }

    private fun readInt(
        input: TextInputEditText,
        layout: TextInputLayout,
        min: Int,
        max: Int
    ): Int? {
        val value = input.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value !in min..max) {
            layout.error = fragment.getString(R.string.settings_llm_number_invalid)
            return null
        }
        return value
    }

    private fun currentTypedApiKey(): String? {
        val value = apiKeyInput.text?.toString()?.trim().orEmpty()
        if (value.isBlank() || LlmSettingsRepository.hasMaskedShape(value)) return null
        return value
    }

    private fun bindApiKeyStatus() {
        val masked = repository.getMaskedApiKey()
        if (masked.isBlank()) {
            apiKeyInput.setText("")
            apiKeyStatusText.text = fragment.getString(R.string.settings_llm_api_key_not_saved)
        } else {
            apiKeyInput.setText(masked)
            apiKeyStatusText.text = fragment.getString(R.string.settings_llm_api_key_saved, masked)
        }
    }

    private fun resolveSelectedPreset(): LlmProviderPreset {
        val displayName = providerInput.text?.toString().orEmpty()
        return LlmProviderPresets.all.firstOrNull { it.displayName == displayName } ?: selectedPreset
    }

    private fun authHeaderOptions(): List<Pair<String, LlmAuthHeaderType>> {
        return listOf(
            fragment.getString(R.string.settings_llm_auth_bearer) to LlmAuthHeaderType.BEARER_AUTHORIZATION,
            fragment.getString(R.string.settings_llm_auth_google) to LlmAuthHeaderType.X_GOOG_API_KEY
        )
    }

    private fun authHeaderLabel(type: LlmAuthHeaderType): String {
        return authHeaderOptions().first { it.second == type }.first
    }

    private fun resolveAuthHeaderType(): LlmAuthHeaderType {
        val label = authHeaderInput.text?.toString().orEmpty()
        return authHeaderOptions().firstOrNull { it.first == label }?.second ?: selectedPreset.authHeaderType
    }

    private fun clearInputErrors() {
        baseUrlInputLayout.error = null
        modelInputLayout.error = null
        temperatureInputLayout.error = null
        timeoutInputLayout.error = null
        maxTokensInputLayout.error = null
    }
}
