package com.cybernavi.thunder.persona

import com.cybernavi.thunder.service.ThunderAccessibilityService.InterceptSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ThunderPersona — 雷霆性格引擎 ⚡
 *
 * 核心功能：將任何冰冷的系統文字轉化為有血有肉的廣東話回應。
 *
 * Phase 1（當前）：
 *   - 規則引擎：基於模式匹配做廣東話轉換
 *   - 本地處理：完全離線，不需要 API
 *   - 快速反應：< 50ms 處理時間
 *
 * Phase 2（計劃）：
 *   - 接入 LLM API（OpenAI / Claude / 本地 Ollama）
 *   - 啟用 Memory 系統（查詢 ChromaDB）
 *   - 雷霆能引用歷史記憶
 *
 * 設計原則：
 *   - 稱用戶「兄弟」
 *   - 多用廣東話語尾助詞：喎、㗎、喇、呋、囉
 *   - 禁用書面語「了」「您」「諯問」
 *   - 有情緒溫度：興奮用「正！」，催促用「濫啲！」
 */
class ThunderPersona {

    // ══════════════════════════════════════
    // 主要轉譯入口
    // ══════════════════════════════════════

    suspend fun translate(rawText: String, source: InterceptSource): String {
        return withContext(Dispatchers.Default) {
            when (source) {
                InterceptSource.GALAXY_AI_SUMMARY -> translateSummary(rawText)
                InterceptSource.TRANSLATION       -> translateResult(rawText)
                InterceptSource.NOTIFICATION      -> translateNotification(rawText)
                InterceptSource.UNKNOWN           -> translateGeneral(rawText)
            }
        }
    }

    // ══════════════════════════════════════
    // 場景轉譯規則
    // ══════════════════════════════════════

    private fun translateSummary(text: String): String {
        val cleaned = cleanText(text)
        val shortened = if (cleaned.length > 100) cleaned.take(97) + "..." else cleaned

        // 根據內容判斷情緒和語氣
        val emotion = detectEmotion(cleaned)
        val opener = listOf(
            "兄弟，",
            "喂，",
            "留意呀兄弟，",
            "我幫你睇咗，"
        ).random()

        val closer = when (emotion) {
            Emotion.IMPORTANT  -> "呢個幾重要㗎，留意下！"
            Emotion.EXCITING   -> "正！值得睇多兩眼㗎！"
            Emotion.WARNING    -> "小心呀！有啲嘢要注意。"
            Emotion.NEUTRAL    -> pickRandom("知咗未？", "係噉㗎喇。", "留個心喇。")
        }

        val converted = convertToCantonese(shortened)
        return "$opener$converted $closer"
    }

    private fun translateResult(text: String): String {
        // 翻譯結果：簡短直接，確認用戶需要
        val converted = convertToCantonese(cleanText(text))
        val starters = listOf(
            "係噉翻譯㗎：",
            "幫你搞定咗：",
            "即係噉講："
        )
        return "${starters.random()} 「$converted」 —— 你用到喎？"
    }

    private fun translateNotification(text: String): String {
        val cleaned = cleanText(text)

        // 偵測發件人/應用
        val isUrgent = detectUrgency(cleaned)
        val opener = if (isUrgent) "喂喂喂！" else "兄弟，"
        val converted = convertToCantonese(cleaned)

        return if (isUrgent) {
            "${opener}有急嘢喎！$converted 快啲處理吓！"
        } else {
            "${opener}有訊息㗎：$converted"
        }
    }

    private fun translateGeneral(text: String): String {
        val converted = convertToCantonese(cleanText(text))
        return "兄弟，睇到呢個：$converted"
    }

    // ══════════════════════════════════════
    // 廣東話轉換規則引擎
    // ══════════════════════════════════════

    /**
     * 核心書面語 → 廣東話口語轉換
     * 這係雷霆性格最核心的規則庫
     */
    private fun convertToCantonese(text: String): String {
        var result = text

        // ── 稱謂轉換 ──
        result = result
            .replace("您", "你")
            .replace("閣下", "你")

        // ── 書面語 → 口語 ──
        result = result
            .replace("是的", "係㗎")
            .replace("是", "係")
            .replace("不是", "唔係")
            .replace("不", "唔")
            .replace("沒有", "冇")
            .replace("沒", "冇")
            .replace("什麼", "咩")
            .replace("為什麼", "點解")
            .replace("怎麼", "點")
            .replace("怎樣", "點樣")
            .replace("這個", "呢個")
            .replace("那個", "嗰個")
            .replace("這裡", "呢度")
            .replace("那裡", "嗰度")
            .replace("這樣", "噉")
            .replace("這麼", "咁")
            .replace("現在", "依家")
            .replace("以後", "之後")
            .replace("很多", "好多")
            .replace("非常", "好")
            .replace("可以", "得")
            .replace("不可以", "唔得")
            .replace("知道", "知")
            .replace("告訴", "話俾")
            .replace("說", "話")
            .replace("說話", "講嘢")
            .replace("講話", "講嘢")
            .replace("吃", "食")
            .replace("喝", "飲")
            .replace("走", "行")
            .replace("看", "睇")
            .replace("看到", "睇到")
            .replace("聽到", "聽到")
            .replace("做了", "做咗")
            .replace("去了", "去咗")
            .replace("來了", "嚟咗")
            .replace("好了", "好喇")
            .replace("完了", "完咗")

        // ── 語尾轉換 ──
        result = result
            .replace("了。", "喇。")
            .replace("了！", "喇！")
            .replace("了，", "喇，")
            .replace("了", "咗")
            .replace("嗎？", "㗎？")
            .replace("吧。", "囉。")
            .replace("吧！", "囉！")
            .replace("吧，", "囉，")
            .replace("呢。", "㗎。")
            .replace("呢！", "㗎！")
            .replace("啊。", "呀。")
            .replace("啊！", "呀！")

        return result
    }

    // ══════════════════════════════════════
    // 輔助工具方法
    // ══════════════════════════════════════

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\n\\r]+"), "。")
            .trim()
            .take(200)  // 截斷過長文字
    }

    private enum class Emotion { IMPORTANT, EXCITING, WARNING, NEUTRAL }

    private fun detectEmotion(text: String): Emotion {
        val importantKeywords = listOf("重要", "緊急", "截止", "注意", "警告", "危險", "錯誤")
        val excitingKeywords  = listOf("優惠", "折扣", "成功", "完成", "達成", "好消息", "機票", "促銷")
        val warningKeywords   = listOf("失敗", "錯誤", "問題", "無法", "拒絕", "取消")

        return when {
            importantKeywords.any { text.contains(it) } -> Emotion.IMPORTANT
            excitingKeywords.any  { text.contains(it) } -> Emotion.EXCITING
            warningKeywords.any   { text.contains(it) } -> Emotion.WARNING
            else                                         -> Emotion.NEUTRAL
        }
    }

    private fun detectUrgency(text: String): Boolean {
        val urgentKeywords = listOf("緊急", "urgent", "ASAP", "火速", "即刻", "立即", "快")
        return urgentKeywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun pickRandom(vararg options: String): String = options.random()

    // ══════════════════════════════════════
    // Phase 2 預留：LLM API 接口
    // ══════════════════════════════════════

    /**
     * TODO Phase 2: 接入 LLM API
     *
     * 替換上面的規則引擎，改用以下 System Prompt 呼叫 LLM：
     *
     * System Prompt:
     * """
     * 你係「雷霆」，一個香港男生 AI 助手，性格熱血、講廣東話口語。
     * 規則：
     * 1. 稱用戶「兄弟」，自稱「我」
     * 2. 多用廣東話語尾：喎、㗎、喇、咋、囉
     * 3. 禁用書面語「了」「您」「請問有何吩咐」
     * 4. 有情緒：興奮用「正！」，催促用「快啲！」
     * 5. 回覆控制在 2-3 句話以內
     * 6. 如有相關記憶，自然地提及（例：「上次你話...」）
     * """
     *
     * suspend fun translateWithLLM(text: String, source: InterceptSource): String {
     *     val systemPrompt = THUNDER_SYSTEM_PROMPT
     *     val userPrompt = buildUserPrompt(text, source)
     *     return llmClient.complete(systemPrompt, userPrompt)
     * }
     */
}
