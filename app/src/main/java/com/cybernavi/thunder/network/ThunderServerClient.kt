package com.cybernavi.thunder.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ThunderServerClient — Phase 2 家用伺服器連接客戶端
 *
 * 當 Phase 2 家用伺服器就緒後，ThunderAccessibilityService
 * 可以將截獲的文字透過此客戶端發送至伺服器，
 * 獲得 LLM 驅動的真正廣東話回應（取代 Phase 1 規則式翻譯）。
 *
 * 使用方式（在 ThunderAccessibilityService 中）：
 *   val client = ThunderServerClient(serverUrl = "http://100.x.x.x:8765")
 *   val response = client.translate(text, source)
 */
class ThunderServerClient(
    private val serverUrl: String = "http://100.x.x.x:8765"  // Tailscale IP
) {
    private val TAG = "ThunderServerClient"
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)   // LLM 推理需要時間
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // ══════════════════════════════════════
    // 主要翻譯端點
    // ══════════════════════════════════════

    /**
     * 發送截獲文字到伺服器，獲得雷霆廣東話回應
     *
     * @return TranslateResult 成功時回傳，失敗時回傳 null（呼叫方應降級至本地翻譯）
     */
    suspend fun translate(text: String, source: String): TranslateResult? =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(
                    mapOf("text" to text, "source" to source)
                ).toRequestBody(json)

                val request = Request.Builder()
                    .url("$serverUrl/api/v1/translate")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    gson.fromJson(body, TranslateResult::class.java)
                } else {
                    Log.w(TAG, "伺服器回應錯誤: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Log.d(TAG, "伺服器連接失敗 (將降級至本地翻譯): ${e.message}")
                null
            }
        }

    /**
     * 健康檢查 — 確認伺服器是否在線
     */
    suspend fun isServerOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // ══════════════════════════════════════
    // 資料模型
    // ══════════════════════════════════════

    data class TranslateResult(
        val thunder_response: String,
        val emotion: String,
        val memories_used: Int = 0
    )
}
