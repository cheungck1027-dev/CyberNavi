package com.cybernavi.thunder.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property — 每個 Context 共用同一 DataStore 實例
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cybernavi_settings")

/**
 * SettingsStore — 持久化設定（DataStore Preferences）
 *
 * 目前儲存：
 *   - server_url: Phase 2 家用伺服器 URL，留空 = 離線規則模式
 */
object SettingsStore {

    private val SERVER_URL_KEY = stringPreferencesKey("server_url")

    // 以 Flow 形式訂閱 server URL（MainActivity 可即時更新 UI）
    fun getServerUrlFlow(context: Context): Flow<String> =
        context.applicationContext.dataStore.data
            .map { prefs -> prefs[SERVER_URL_KEY] ?: "" }

    // 一次性讀取（掛起函數，供 Service 啟動時使用）
    suspend fun getServerUrl(context: Context): String =
        getServerUrlFlow(context).first()

    // 寫入新 URL
    suspend fun saveServerUrl(context: Context, url: String) {
        context.applicationContext.dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = url.trim()
        }
    }
}
