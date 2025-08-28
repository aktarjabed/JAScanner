package com.jascanner.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val TSA_URL = stringPreferencesKey("tsa_url")
    fun observeTsaUrl() = dataStore.data.map { it[TSA_URL] ?: "http://timestamp.sectigo.com" }
    suspend fun setTsaUrl(url: String) = dataStore.edit { it[TSA_URL] = url }
}

