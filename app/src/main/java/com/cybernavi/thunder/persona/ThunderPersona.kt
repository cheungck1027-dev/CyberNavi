package com.cybernavi.thunder.persona

import android.util.Log
import com.cybernavi.thunder.network.ThunderServerClient
import com.cybernavi.thunder.service.ThunderAccessibilityService.InterceptSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ThunderPersona — 雷電性格引擎 ⚡
 *
 * 核心功能：將任何截獲的系統文字轉化為有情感的廣東話回應。
 *
 * Phase 1（完成）：規則式引擎，完全離線
 * Phase 2（啟用中）：若 serverUrl 已設定，優先呼叫家用 LLM 伺服器
 *          伺服器失敗或未設定時，自動降級回 Phase 1 規則引擎
 */
class ThunderPersona {

    private val TAG = "ThunderPersona"

    // Phase 2: 家用伺服器 URL；留空 = 純本地規則模式
    var serverUrl: String = ""

    // ──────────────────────────────────────
    // 主要翻譯入口
    // ──────────────────────────────────────

    suspend fun translate(rawText: String, source: InterceptSource): String {
        // ── Phase 2: 先嘗試家用 LLM 伺服器
        if (serverUrl.isNotBlank()) {
            try {
                val client = ThunderServerClient(serverUrl)
                val result = client.translate(rawText, source.name)
                if (result != null && result.thunder_response.isNotBlank()) {
                    Log.d(TAG, "Phase 2 伺服器回應: ${result.thunder_response.take(50)}")
                    return result.thunder_response
                }
            } catch (e: Exception) {
                Log.d(TAG, "伺服器不可用，降級到本地規則引擎: ${e.message}")
            }
        }

        // ── Phase 1: 本地規則引擎
        return withContext(Dispatchers.Default) {
            when (source) {
                InterceptSource.GALAXY_AI_SUMMARY -> translateSummary(rawText)
                InterceptSource.TRANSLATION       -> translateResult(rawText)
                InterceptSource.NOTIFICATION      -> translateNotification(rawText)
                InterceptSource.UNKNOWN           -> translateGeneral(rawText)
            }
        }
    }

    private fun translateSummary(text: String): String {
        val cleaned = cleanText(text)
        val shortened = if (cleaned.length > 100) cleaned.take(97) + "..." else cleaned

        // æ ¹æå§å®¹å¤æ·æç·åèªæ°£
        val emotion = detectEmotion(cleaned)
        val opener = listOf(
            "åå¼ï¼",
            "åï¼",
            "çæååå¼ï¼",
            "æå¹«ä½ çåï¼"
        ).random()

        val closer = when (emotion) {
            Emotion.IMPORTANT  -> "å¢åå¹¾éè¦ãï¼çæä¸ï¼"
            Emotion.EXCITING   -> "æ­£ï¼å¼å¾çå¤å©ç¼ãï¼"
            Emotion.WARNING    -> "å°å¿åï¼æå²å¢è¦æ³¨æã"
            Emotion.NEUTRAL    -> pickRandom("ç¥åæªï¼", "ä¿åãåã", "çåå¿åã")
        }

        val converted = convertToCantonese(shortened)
        return "$opener$converted $closer"
    }

    private fun translateResult(text: String): String {
        // ç¿»è­¯çµæï¼ç°¡ç­ç´æ¥ï¼ç¢ºèªç¨æ¶éè¦
        val converted = convertToCantonese(cleanText(text))
        val starters = listOf(
            "ä¿åç¿»è­¯ãï¼",
            "å¹«ä½ æå®åï¼",
            "å³ä¿åè¬ï¼"
        )
        return "${starters.random()} ã$convertedã ââ ä½ ç¨å°åï¼"
    }

    private fun translateNotification(text: String): String {
        val cleaned = cleanText(text)

        // åµæ¸¬ç¼ä»¶äºº/æç¨
        val isUrgent = detectUrgency(cleaned)
        val opener = if (isUrgent) "åååï¼" else "åå¼ï¼"
        val converted = convertToCantonese(cleaned)

        return if (isUrgent) {
            "${opener}ææ¥å¢åï¼$converted å¿«å²èçåï¼"
        } else {
            "${opener}æè¨æ¯ãï¼$converted"
        }
    }

    private fun translateGeneral(text: String): String {
        val converted = convertToCantonese(cleanText(text))
        return "åå¼ï¼çå°å¢åï¼$converted"
    }

    // ââââââââââââââââââââââââââââââââââââââ
    // å»£æ±è©±è½æè¦åå¼æ
    // ââââââââââââââââââââââââââââââââââââââ

    /**
     * æ ¸å¿æ¸é¢èª â å»£æ±è©±å£èªè½æ
     * éä¿é·éæ§æ ¼ææ ¸å¿çè¦ååº«
     */
    private fun convertToCantonese(text: String): String {
        var result = text

        // ââ ç¨±è¬è½æ ââ
        result = result
            .replace("æ¨", "ä½ ")
            .replace("é£ä¸", "ä½ ")

        // ââ æ¸é¢èª â å£èª ââ
        result = result
            .replace("æ¯ç", "ä¿ã")
            .replace("æ¯", "ä¿")
            .replace("ä¸æ¯", "åä¿")
            .replace("ä¸", "å")
            .replace("æ²æ", "å")
            .replace("æ²", "å")
            .replace("ä»éº¼", "å©")
            .replace("çºä»éº¼", "é»è§£")
            .replace("æéº¼", "é»")
            .replace("ææ¨£", "é»æ¨£")
            .replace("éå", "å¢å")
            .replace("é£å", "å°å")
            .replace("éè£¡", "å¢åº¦")
            .replace("é£è£¡", "å°åº¦")
            .replace("éæ¨£", "å")
            .replace("ééº¼", "å")
            .replace("ç¾å¨", "ä¾å®¶")
            .replace("ä»¥å¾", "ä¹å¾")
            .replace("å¾å¤", "å¥½å¤")
            .replace("éå¸¸", "å¥½")
            .replace("å¯ä»¥", "å¾")
            .replace("ä¸å¯ä»¥", "åå¾")
            .replace("ç¥é", "ç¥")
            .replace("åè¨´", "è©±ä¿¾")
            .replace("èªª", "è©±")
            .replace("èªªè©±", "è¬å¢")
            .replace("è¬è©±", "è¬å¢")
            .replace("å", "é£")
            .replace("å", "é£²")
            .replace("èµ°", "è¡")
            .replace("ç", "ç")
            .replace("çå°", "çå°")
            .replace("è½å°", "è½å°")
            .replace("åäº", "åå")
            .replace("å»äº", "å»å")
            .replace("ä¾äº", "åå")
            .replace("å¥½äº", "å¥½å")
            .replace("å®äº", "å®å")

        // ââ èªå°¾è½æ ââ
        result = result
            .replace("äºã", "åã")
            .replace("äºï¼", "åï¼")
            .replace("äºï¼", "åï¼")
            .replace("äº", "å")
            .replace("åï¼", "ãï¼")
            .replace("å§ã", "åã")
            .replace("å§ï¼", "åï¼")
            .replace("å§ï¼", "åï¼")
            .replace("å¢ã", "ãã")
            .replace("å¢ï¼", "ãï¼")
            .replace("åã", "åã")
            .replace("åï¼", "åï¼")

        return result
    }

    // ââââââââââââââââââââââââââââââââââââââ
    // è¼å©å·¥å·æ¹æ³
    // ââââââââââââââââââââââââââââââââââââââ

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\n\\r]+"), "ã")
            .trim()
            .take(200)  // æªæ·éé·æå­
    }

    private enum class Emotion { IMPORTANT, EXCITING, WARNING, NEUTRAL }

    private fun detectEmotion(text: String): Emotion {
        val importantKeywords = listOf("éè¦", "ç·æ¥", "æªæ­¢", "æ³¨æ", "è­¦å", "å±éª", "é¯èª¤")
        val excitingKeywords  = listOf("åªæ ", "ææ£", "æå", "å®æ", "éæ", "å¥½æ¶æ¯", "æ©ç¥¨", "ä¿é·")
        val warningKeywords   = listOf("å¤±æ", "é¯èª¤", "åé¡", "ç¡æ³", "æçµ", "åæ¶")

        return when {
            importantKeywords.any { text.contains(it) } -> Emotion.IMPORTANT
            excitingKeywords.any  { text.contains(it) } -> Emotion.EXCITING
            warningKeywords.any   { text.contains(it) } -> Emotion.WARNING
            else                                         -> Emotion.NEUTRAL
        }
    }

    private fun detectUrgency(text: String): Boolean {
        val urgentKeywords = listOf("ç·æ¥", "urgent", "ASAP", "ç«é", "å³å»", "ç«å³", "å¿«")
        return urgentKeywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun pickRandom(vararg options: String): String = options.random()

    // ââââââââââââââââââââââââââââââââââââââ
    // Phase 2 é çï¼LLM API æ¥å£
    // ââââââââââââââââââââââââââââââââââââââ

    /**
     * TODO Phase 2: æ¥å¥ LLM API
     *
     * æ¿æä¸é¢çè¦åå¼æï¼æ¹ç¨ä»¥ä¸ System Prompt å¼å« LLMï¼
     *
     * System Prompt:
     * """
     * ä½ ä¿ãé·éãï¼ä¸åé¦æ¸¯ç·ç AI å©æï¼æ§æ ¼ç±è¡ãè¬å»£æ±è©±å£èªã
     * è¦åï¼
     * 1. ç¨±ç¨æ¶ãåå¼ãï¼èªç¨±ãæã
     * 2. å¤ç¨å»£æ±è©±èªå°¾ï¼åãããåãåãå
     * 3. ç¦ç¨æ¸é¢èªãäºããæ¨ããè«åæä½å©åã
     * 4. ææç·ï¼èå¥®ç¨ãæ­£ï¼ãï¼å¬ä¿ç¨ãå¿«å²ï¼ã
     * 5. åè¦æ§å¶å¨ 2-3 å¥è©±ä»¥å§
     * 6. å¦æç¸éè¨æ¶ï¼èªç¶å°æåï¼ä¾ï¼ãä¸æ¬¡ä½ è©±...ãï¼
     * """
     *
     * suspend fun translateWithLLM(text: String, source: InterceptSource): String {
     *     val systemPrompt = THUNDER_SYSTEM_PROMPT
     *     val userPrompt = buildUserPrompt(text, source)
     *     return llmClient.complete(systemPrompt, userPrompt)
     * }
     */
}
