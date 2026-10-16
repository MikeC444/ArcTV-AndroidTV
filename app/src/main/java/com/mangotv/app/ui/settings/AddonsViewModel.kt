package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.InstalledAddon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddonsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MangoTvApplication).container
    private val repository = container.addonRepository

    val installedAddons: StateFlow<List<InstalledAddon>> = repository.installedAddons

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** What the last refresh found, shown beside the button ("2 new addons added"); null before the first one. */
    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage

    fun setEnabled(manifestUrl: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(manifestUrl, enabled) }
    }

    fun remove(manifestUrl: String) {
        viewModelScope.launch { repository.removeAddon(manifestUrl) }
    }

    /**
     * Looks at the account's addons again, for addons added or changed on another device (the phone) while this app was already open. Changes made here
     * that never reached the server are sent first, so pulling can't wipe them.
     */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        _refreshMessage.value = null
        viewModelScope.launch {
            try {
                val before = repository.installedAddons.value.map { it.manifestUrl }.toSet()
                container.addonSyncRepository.retryPending()
                val pulled = container.addonSyncRepository.pullFromServer()
                _refreshMessage.value = if (!pulled) {
                    "Couldn't refresh. Check your connection and try again."
                } else {
                    val added = repository.installedAddons.value.count { it.manifestUrl !in before }
                    when (added) {
                        0 -> "Your addons are up to date."
                        1 -> "1 new addon added."
                        else -> "$added new addons added."
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }
}
