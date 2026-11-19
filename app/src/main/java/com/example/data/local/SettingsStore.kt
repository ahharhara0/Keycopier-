package com.example.data.local

import android.content.SharedPreferences
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AppThemeMode
import com.example.domain.model.MeasurementUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        val lang = prefs.getString("language", AppLanguage.ENGLISH.code)
            ?.let { code -> AppLanguage.entries.firstOrNull { it.code == code } }
            ?: AppLanguage.ENGLISH
        val theme = prefs.getString("theme", AppThemeMode.SYSTEM.name)
            ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: AppThemeMode.SYSTEM
        val unit = prefs.getString("preferredUnit", MeasurementUnit.MM.name)
            ?.let { runCatching { MeasurementUnit.valueOf(it) }.getOrNull() }
            ?: MeasurementUnit.MM
        val biometric = prefs.getBoolean("biometricEnabled", false)
        val autoLock = prefs.getInt("autoLockSeconds", 30)
        val dpiOverride = prefs.getFloat("screenDpiOverride", 0f)
        val onboarded = prefs.getBoolean("onboardingCompleted", false)
        val templatesCount = prefs.getInt("templatesGeneratedCount", 0)

        return AppSettings(
            language = lang,
            theme = theme,
            preferredUnit = unit,
            biometricEnabled = biometric,
            autoLockSeconds = autoLock,
            screenDpiOverride = dpiOverride,
            onboardingCompleted = onboarded,
            templatesGeneratedCount = templatesCount
        )
    }

    fun updateLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.code).apply()
        _settings.value = _settings.value.copy(language = language)
    }

    fun updateTheme(theme: AppThemeMode) {
        prefs.edit().putString("theme", theme.name).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun updateUnit(unit: MeasurementUnit) {
        prefs.edit().putString("preferredUnit", unit.name).apply()
        _settings.value = _settings.value.copy(preferredUnit = unit)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometricEnabled", enabled).apply()
        _settings.value = _settings.value.copy(biometricEnabled = enabled)
    }

    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit().putInt("autoLockSeconds", seconds).apply()
        _settings.value = _settings.value.copy(autoLockSeconds = seconds)
    }

    fun setScreenDpiOverride(dpi: Float) {
        prefs.edit().putFloat("screenDpiOverride", dpi).apply()
        _settings.value = _settings.value.copy(screenDpiOverride = dpi)
    }

    fun setOnboardingCompleted(done: Boolean) {
        prefs.edit().putBoolean("onboardingCompleted", done).apply()
        _settings.value = _settings.value.copy(onboardingCompleted = done)
    }

    fun incrementTemplatesGenerated() {
        val next = _settings.value.templatesGeneratedCount + 1
        prefs.edit().putInt("templatesGeneratedCount", next).apply()
        _settings.value = _settings.value.copy(templatesGeneratedCount = next)
    }
}
