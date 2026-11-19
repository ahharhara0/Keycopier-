package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsStore
import com.example.data.repository.KeyVaultRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AppThemeMode
import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyRecord
import com.example.domain.model.MeasurementUnit
import com.example.export.ExportFormat
import com.example.export.ExportManager
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LockUiState(
    val hasPinConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val failedAttempts: Int = 0,
    val maxAttempts: Int = 5
)

data class VaultUiState(
    val allKeys: List<KeyRecord> = emptyList(),
    val filteredKeys: List<KeyRecord> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: KeyCategory? = null,
    val isGridView: Boolean = false,
    val attachmentCount: Int = 0,
    val storageBytes: Long = 0L,
    val statusMessage: String? = null
)

class VaultViewModel(
    private val repository: KeyVaultRepository,
    private val settingsStore: SettingsStore,
    private val cryptoManager: KeystoreCryptoManager,
    private val exportManager: ExportManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _lockState = MutableStateFlow(
        LockUiState(
            hasPinConfigured = cryptoManager.hasPinConfigured(),
            isUnlocked = false
        )
    )
    val lockState: StateFlow<LockUiState> = _lockState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<KeyCategory?>(null)
    private val _isGridView = MutableStateFlow(false)
    private val _storageBytes = MutableStateFlow(repository.calculateVaultStorageBytes())
    private val _statusMessage = MutableStateFlow<String?>(null)

    private var backgroundedAtMs: Long = 0L

    private val vaultFiltersFlow = combine(
        _searchQuery,
        _selectedCategory,
        _isGridView,
        _storageBytes,
        _statusMessage
    ) { query, category, grid, storage, status ->
        FilterParams(query, category, grid, storage, status)
    }

    val vaultState: StateFlow<VaultUiState> = combine(
        repository.allKeys,
        repository.attachmentCount,
        vaultFiltersFlow
    ) { keys, attCount, params ->
        val trimmed = params.query.trim()
        val filtered = keys.filter { record ->
            val matchesCategory = params.category == null || record.category == params.category
            val matchesQuery = trimmed.isEmpty() ||
                record.name.contains(trimmed, ignoreCase = true) ||
                record.bittingCode.contains(trimmed, ignoreCase = true) ||
                record.keyStandardId.contains(trimmed, ignoreCase = true) ||
                record.tags.contains(trimmed, ignoreCase = true) ||
                record.notes.contains(trimmed, ignoreCase = true)
            matchesCategory && matchesQuery
        }
        VaultUiState(
            allKeys = keys,
            filteredKeys = filtered,
            searchQuery = params.query,
            selectedCategory = params.category,
            isGridView = params.grid,
            attachmentCount = attCount,
            storageBytes = params.storageBytes,
            statusMessage = params.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    private data class FilterParams(
        val query: String,
        val category: KeyCategory?,
        val grid: Boolean,
        val storageBytes: Long,
        val statusMessage: String?
    )

    // --- Security / Lock ---

    fun createAndUnlockPin(pin: String) {
        cryptoManager.setPin(pin)
        settingsStore.setOnboardingCompleted(true)
        _lockState.value = LockUiState(
            hasPinConfigured = true,
            isUnlocked = true,
            failedAttempts = 0
        )
    }

    fun attemptPinUnlock(pin: String): Boolean {
        val ok = cryptoManager.verifyPin(pin)
        if (ok) {
            _lockState.value = _lockState.value.copy(
                isUnlocked = true,
                failedAttempts = 0
            )
        } else {
            _lockState.value = _lockState.value.copy(
                failedAttempts = _lockState.value.failedAttempts + 1
            )
        }
        return ok
    }

    fun unlockWithBiometric() {
        _lockState.value = _lockState.value.copy(
            isUnlocked = true,
            failedAttempts = 0
        )
    }

    fun changePin(newPin: String) {
        cryptoManager.setPin(newPin)
        _lockState.value = _lockState.value.copy(hasPinConfigured = true)
    }

    fun lockNow() {
        if (_lockState.value.hasPinConfigured) {
            _lockState.value = _lockState.value.copy(isUnlocked = false)
        }
    }

    fun onAppBackgrounded() {
        backgroundedAtMs = System.currentTimeMillis()
        val timeout = settings.value.autoLockSeconds
        if (timeout == 0 && _lockState.value.hasPinConfigured) {
            _lockState.value = _lockState.value.copy(isUnlocked = false)
        }
    }

    fun onAppForegrounded() {
        val timeout = settings.value.autoLockSeconds
        if (timeout >= 0 && backgroundedAtMs > 0L && _lockState.value.hasPinConfigured) {
            val elapsedSec = (System.currentTimeMillis() - backgroundedAtMs) / 1000L
            if (elapsedSec >= timeout) {
                _lockState.value = _lockState.value.copy(isUnlocked = false)
            }
        }
        backgroundedAtMs = 0L
    }

    // --- Library ---

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: KeyCategory?) {
        _selectedCategory.value = category
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleFavorite(record: KeyRecord) {
        viewModelScope.launch {
            repository.toggleFavorite(record.id, !record.isFavorite)
        }
    }

    fun deleteKey(id: Long) {
        viewModelScope.launch {
            repository.deleteKey(id)
            refreshStorageStats()
        }
    }

    fun refreshStorageStats() {
        _storageBytes.value = repository.calculateVaultStorageBytes()
    }

    // --- Export & Backup ---

    fun exportKeyToUri(record: KeyRecord, format: ExportFormat, uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val bytes = exportManager.generateRecordExportBytes(record, format)
                exportManager.writeBytesToUri(uri, bytes)
                settingsStore.incrementTemplatesGenerated()
                exportManager.suggestedFileName(record, format)
            }.onSuccess { fileName ->
                onDone(fileName)
            }.onFailure { err ->
                _statusMessage.value = err.message
            }
        }
    }

    fun shareKeyTemplate(record: KeyRecord, format: ExportFormat) {
        viewModelScope.launch {
            runCatching {
                val bytes = exportManager.generateRecordExportBytes(record, format)
                val fileName = exportManager.suggestedFileName(record, format)
                settingsStore.incrementTemplatesGenerated()
                exportManager.shareBytes(fileName, format.mimeType, bytes)
            }.onFailure { err ->
                _statusMessage.value = err.message
            }
        }
    }

    fun shareKeySpecText(record: KeyRecord) {
        exportManager.sharePlainTextSpec(record)
    }

    fun exportVaultBackupToUri(
        uri: Uri,
        encrypted: Boolean,
        passphrase: String,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val records = vaultState.value.allKeys
                val bytes = exportManager.generateVaultBackupBytes(records, encrypted, passphrase)
                exportManager.writeBytesToUri(uri, bytes)
                if (encrypted) "keycopier_vault_backup.kcb" else "keycopier_vault_backup.json"
            }.onSuccess(onDone)
                .onFailure { _statusMessage.value = it.message }
        }
    }

    fun importBackupFromUri(uri: Uri, passphrase: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val bytes = exportManager.readBytesFromUri(uri)
                val records = exportManager.parseVaultBackupBytes(bytes, passphrase)
                val count = repository.importRecords(records)
                refreshStorageStats()
                count
            }.onSuccess(onDone)
                .onFailure { _statusMessage.value = it.message ?: "Import failed" }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // --- Settings ---

    fun setLanguage(language: AppLanguage) = settingsStore.updateLanguage(language)
    fun setTheme(theme: AppThemeMode) = settingsStore.updateTheme(theme)
    fun setUnit(unit: MeasurementUnit) = settingsStore.updateUnit(unit)
    fun setBiometricEnabled(enabled: Boolean) = settingsStore.setBiometricEnabled(enabled)
    fun setAutoLockSeconds(seconds: Int) = settingsStore.setAutoLockSeconds(seconds)
    fun setScreenDpiOverride(dpi: Float) = settingsStore.setScreenDpiOverride(dpi)

    class Factory(
        private val repository: KeyVaultRepository,
        private val settingsStore: SettingsStore,
        private val cryptoManager: KeystoreCryptoManager,
        private val exportManager: ExportManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VaultViewModel(repository, settingsStore, cryptoManager, exportManager) as T
        }
    }
}
