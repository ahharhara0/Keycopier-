package com.example

import android.app.Application
import com.example.data.local.KeyCopierDatabase
import com.example.data.local.SettingsStore
import com.example.data.repository.KeyVaultRepository
import com.example.export.ExportManager
import com.example.security.KeystoreCryptoManager

class AppContainer(application: Application) {
    val cryptoManager: KeystoreCryptoManager by lazy {
        KeystoreCryptoManager(application)
    }
    val settingsStore: SettingsStore by lazy {
        SettingsStore(cryptoManager.securePrefs)
    }
    val database: KeyCopierDatabase by lazy {
        val passphrase = cryptoManager.getOrCreateDatabasePassphrase()
        KeyCopierDatabase.create(application, passphrase)
    }
    val repository: KeyVaultRepository by lazy {
        KeyVaultRepository(application, database.keyCopierDao(), cryptoManager)
    }
    val exportManager: ExportManager by lazy {
        ExportManager(application, cryptoManager)
    }
}

class KeyCopierApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
