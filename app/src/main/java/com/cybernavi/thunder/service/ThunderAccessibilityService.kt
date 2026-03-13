package com.cybernavi.thunder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cybernavi.thunder.persona.ThunderPersona
import kotlinx.coroutines.*

/**
 * ThunderAccessibilityService — 系統 AI 輸出截獲模組
 *
 * 這係整個「寄生架構」嘅核心技術。
 * 監聽 AccessibilityEvent，當偵測到系統 AI（Galaxy AI、翻譯等）
 * 輸出文字時，截獲並交由 ThunderPersona 轉譯成廣東話。
 *
 * 監聽目標：
 * - Samsung Galaxy AI 摘要彈窗
 * - Samsung 翻譯 App
 * - 通知欄內容（WhatsApp/Email 等）
 * - 剪貼板變化（透過 ClipboardManager 另外監聽）
 */
class ThunderAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val persona = ThunderPersona()

    // 防重複處理：記錄最後處理的文字，避免重複觸發
    private var lastProcessedText = ""
    private var lastProcessedTime = 0L
    private val DEBOUNCE_MS = 2000L  // 2秒內同一文字不重複處理

    companion object {
        private const val TAG = "ThunderAccessibility"

        // Samsung Galaxy AI 相關套件名稱
        private val SAMSUNG_PACKAGES = setOf(
            "com.samsung.android.aichatassist",
            "com.samsung.android.aiservice",
            "com.samsung.android.intelligenceservice2",
            "com.samsung.android.galaxyai",
            "com.samsung.android.app.notes",
            "com.samsung.android.email.provider"
        )

        // 翻譯套件
        private val TRANSLATE_PACKAGES = setOf(
            "com.samsung.android.app.translator",
            "com.google.android.apps.translate"
        )

        // 通知套件
        private val NOTIFICATION_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.gm",
            "com.microsoft.teams",
            "org.telegram.messenger",
            "com.instagram.android",
            "com.facebook.orca"
        )

        // 判斷無障礙服務是否已啟用
        fun isEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, ThunderAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(componentName.flattenToString())
        }
    }

    // ══════════════════════════════════════
    // 服務生命周期
    // ══════════════════════════════════════

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "雷霆無障礙服務已連接 ⚡")

        // 動態配置監聽範圍
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 500  // 500ms 防抖
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            // 視窗內容變化 — 主要截獲點
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(packageName, event)
            }

            // 新視窗出現 — 偵測 AI 彈窗
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(packageName, event)
            }

            // 通知 — WhatsApp/Email 等
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(packageName, event)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "無障礙服務被中斷")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ══════════════════════════════════════
    // 事件處理
    // ══════════════════════════════════════

    private fun handleWindowContentChanged(packageName: String, event: AccessibilityEvent) {
        // 只處理我們關心的套件
        if (packageName !in SAMSUNG_PACKAGES &&
            packageName !in TRANSLATE_PACKAGES) return

        // 嘗試從視窗節點提取文字
        val rootNode = rootInActiveWindow ?: return
        val extractedText = extractTextFromNode(rootNode)
        rootNode.recycle()

        if (extractedText.isNotBlank() && extractedText.length > 20) {
            processInterceptedText(
                text = extractedText,
                source = when (packageName) {
                    in SAMSUNG_PACKAGES   -> InterceptSource.GALAXY_AI_SUMMARY
                    in TRANSLATE_PACKAGES -> InterceptSource.TRANSLATION
                    else                  -> InterceptSource.UNKNOWN
                }
            )
        }
    }

    private fun handleWindowStateChanged(packageName: String, event: AccessibilityEvent) {
        if (packageName !in SAMSUNG_PACKAGES) return

        // 偵測 Galaxy AI 浮動面板
        val className = event.className?.toString() ?: return
        if (className.contains("BottomSheet", ignoreCase = true) ||
            className.contains("Dialog", ignoreCase = true) ||
            className.contains("Popup", ignoreCase = true)) {

            // 延遲讀取，等待內容載入
            serviceScope.launch {
                delay(800)
                val rootNode = rootInActiveWindow ?: return@launch
                val text = extractTextFromNode(rootNode)
                rootNode.recycle()
                if (text.length > 30) {
                    processInterceptedText(text, InterceptSource.GALAXY_AI_SUMMARY)
                }
            }
        }
    }

    private fun handleNotification(packageName: String, event: AccessibilityEvent) {
        if (packageName !in NOTIFICATION_PACKAGES) return

        val notificationText = buildString {
            event.text?.forEach { append(it).append(" ") }
        }.trim()

        if (notificationText.isNotBlank()) {
            processInterceptedText(notificationText, InterceptSource.NOTIFICATION)
        }
    }

    // ══════════════════════════════════════
    // 文字提取
    // ══════════════════════════════════════

    /**
     * 遞歸掃描 AccessibilityNodeInfo 樹，提取所有可見文字
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        extractTextsRecursive(node, texts, depth = 0)
        return texts.joinToString("\n").trim()
    }

    private fun extractTextsRecursive(
        node: AccessibilityNodeInfo,
        result: MutableList<String>,
        depth: Int
    ) {
        if (depth > 10) return  // 防止無限遞歸

        // 提取文字
        node.text?.toString()?.takeIf { it.isNotBlank() && it.length > 5 }?.let {
            result.add(it)
        }
        node.contentDescription?.toString()?.takeIf {
            it.isNotBlank() && it.length > 5
        }?.let {
            result.add(it)
        }

        // 遞歸子節點
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractTextsRecursive(child, result, depth + 1)
                child.recycle()
            }
        }
    }

    // ══════════════════════════════════════
    // 核心處理流程
    // ══════════════════════════════════════

    private fun processInterceptedText(text: String, source: InterceptSource) {
        val now = System.currentTimeMillis()

        // 防重複：相同文字 2 秒內只處理一次
        if (text == lastProcessedText && (now - lastProcessedTime) < DEBOUNCE_MS) {
            return
        }

        lastProcessedText = text
        lastProcessedTime = now

        Log.d(TAG, "截獲文字 [${source.name}]: ${text.take(50)}...")

        serviceScope.launch {
            // 1. 通知雷霆開始「思考」
            FloatingWindowService.updateState(
                this@ThunderAccessibilityService,
                FloatingWindowService.STATE_THINKING
            )

            // 2. 用 ThunderPersona 轉譯成廣東話
            val thunderResponse = persona.translate(text, source)

            // 3. 顯示轉譯結果
            FloatingWindowService.sendMessage(
                this@ThunderAccessibilityService,
                thunderResponse,
                text
            )

            Log.d(TAG, "雷霆輸出: $thunderResponse")
        }
    }

    enum class InterceptSource {
        GALAXY_AI_SUMMARY,
        TRANSLATION,
        NOTIFICATION,
        UNKNOWN
    }
}
