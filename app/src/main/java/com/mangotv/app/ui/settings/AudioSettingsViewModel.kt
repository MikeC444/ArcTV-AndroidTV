package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.PlayerPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Backs Settings > Audio's synced part: the preferred audio language for new playback sessions (see PlayerEngine.buildExoPlayer). */
class AudioSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val playerPreferencesRepository = (application as MangoTvApplication).container.playerPreferencesRepository

    val preferences: StateFlow<PlayerPreferences> = playerPreferencesRepository.preferences

    /** [languageCode] is one of AudioLanguageOptions' codes, or null for "no preference". */
    fun setDefaultAudioLanguage(languageCode: String?) {
        viewModelScope.launch { playerPreferencesRepository.setDefaultAudioLanguage(languageCode) }
    }
}
