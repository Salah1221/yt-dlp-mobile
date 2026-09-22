package com.nesco.ytdlpmobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nesco.ytdlpmobile.App

/**
 * Builds [MainViewModel] from the client and the storage the application
 * holds. Small on purpose, so the app needs no injection library.
 */
class MainViewModelFactory(private val app: App) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "this factory builds MainViewModel only, not ${modelClass.name}"
        }
        return MainViewModel(app.client, app.settings, app.downloads) as T
    }
}
