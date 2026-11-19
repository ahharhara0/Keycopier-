package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.example.ui.KeyCopierApp
import com.example.ui.StudioViewModel
import com.example.ui.VaultViewModel

class MainActivity : FragmentActivity() {

    private val appContainer by lazy {
        (application as KeyCopierApplication).container
    }

    private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModel.Factory(
            repository = appContainer.repository,
            settingsStore = appContainer.settingsStore,
            cryptoManager = appContainer.cryptoManager,
            exportManager = appContainer.exportManager
        )
    }

    private val studioViewModel: StudioViewModel by viewModels {
        StudioViewModel.Factory(repository = appContainer.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyCopierApp(
                vaultViewModel = vaultViewModel,
                studioViewModel = studioViewModel
            )
        }
    }

    override fun onStart() {
        super.onStart()
        vaultViewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        vaultViewModel.onAppBackgrounded()
    }
}
