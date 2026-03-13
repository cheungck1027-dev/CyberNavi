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

import com.cybernavi.thunder.settings.SettingsStore
/**
 * ThunderAccessibilityService â ç³»çµ± AI è¼¸åºæªç²æ¨¡çµ
 *
 * éä¿æ´åãå¯çæ¶æ§ãåæ ¸å¿æè¡ã
 * ç£è½ AccessibilityEventï¼ç¶åµæ¸¬å°ç³»çµ± AIï¼Galaxy AIãç¿»è­¯ç­ï¼
 * è¼¸åºæå­æï¼æªç²ä¸¦äº¤ç± ThunderPersona è½è­¯æå»£æ±è©±ã
 *
 * ç£è½ç®æ¨ï¼
 * - Samsung Galaxy AI æè¦å½çª
 * - Samsung ç¿»è­¯ App
 * - éç¥æ¬å§å®¹ï¼WhatsApp/Email ç­ï¼
 * - åªè²¼æ¿è®åï¼éé ClipboardManager å¦å¤ç£è½ï¼
 */
class ThunderAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val persona = ThunderPersona()

    // é²éè¤èçï¼è¨éæå¾èççæå­ï¼é¿åéè¤è§¸ç¼
    private var lastProcessedText = ""
    private var lastProcessedTime = 0L
    private val DEBOUNCE_MS = 2000L  // 2ç§å§åä¸æå­ä¸éè¤èç

    companion object {
        private const val TAG = "ThunderAccessibility"

        // Samsung Galaxy AI ç¸éå¥ä»¶åç¨±
        private val SAMSUNG_PACKAGES = setOf(
            "com.samsung.android.aichatassist",
            "com.samsung.android.aiservice",
            "com.samsung.android.intelligenceservice2",
            "com.samsung.android.galaxyai",
            "com.samsung.android.app.notes",
            "com.samsung.android.email.provider"
        )

        // ç¿»è­¯å¥ä»¶
        private val TRANSLATE_PACKAGES = setOf(
            "com.samsung.android.app.translator",
            "com.google.android.apps.translate"
        )

        // éç¥å¥ä»¶
        private val NOTIFICATION_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.gm",
            "com.microsoft.teams",
            "org.telegram.messenger",
            "com.instagram.android",
            "com.facebook.orca"
        )

        // å¤æ·ç¡éç¤æåæ¯å¦å·²åç¨
        fun isEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, ThunderAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(componentName.flattenToString())
        }
    }

    // ââââââââââââââââââââââââââââââââââââââ
    // æåçå½å¨æ
    // ââââââââââââââââââââââââââââââââââââââ

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "é·éç¡éç¤æåå·²é£æ¥ â¡")

        // Phase 2: 讀取儲存的伺服器 URL，更新 persona.serverUrl
        serviceScope.launch {
            val url = SettingsStore.getServerUrl(this@ThunderAccessibilityService)
            persona.serverUrl = url
            Log.d(TAG, if (url.isNotBlank()) "Phase 2 伺服器已設定: $url" else "Phase 1 本地規則模式")
        }

        // åæéç½®ç£è½ç¯å
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 500  // 500ms é²æ
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            // è¦çªå§å®¹è®å â ä¸»è¦æªç²é»
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(packageName, event)
            }

            // æ°è¦çªåºç¾ â åµæ¸¬ AI å½çª
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(packageName, event)
            }

            // éç¥ â WhatsApp/Email ç­
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(packageName, event)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "ç¡éç¤æåè¢«ä¸­æ·")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ââââââââââââââââââââââââââââââââââââââ
    // äºä»¶èç
    // ââââââââââââââââââââââââââââââââââââââ

    private fun handleWindowContentChanged(packageName: String, event: AccessibilityEvent) {
        // åªèçæåéå¿çå¥ä»¶
        if (packageName !in SAMSUNG_PACKAGES &&
            packageName !in TRANSLATE_PACKAGES) return

        // åè©¦å¾è¦çªç¯é»æåæå­
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

        // åµæ¸¬ Galaxy AI æµ®åé¢æ¿
        val className = event.className?.toString() ?: return
        if (className.contains("BottomSheet", ignoreCase = true) ||
            className.contains("Dialog", ignoreCase = true) ||
            className.contains("Popup", ignoreCase = true)) {

            // å»¶é²è®åï¼ç­å¾å§å®¹è¼å¥
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

    // ââââââââââââââââââââââââââââââââââââââ
    // æå­æå
    // ââââââââââââââââââââââââââââââââââââââ

    /**
     * éæ­¸ææ AccessibilityNodeInfo æ¨¹ï¼æåææå¯è¦æå­
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
        if (depth > 10) return  // é²æ­¢ç¡ééæ­¸

        // æåæå­
        node.text?.toString()?.takeIf { it.isNotBlank() && it.length > 5 }?.let {
            result.add(it)
        }
        node.contentDescription?.toString()?.takeIf {
            it.isNotBlank() && it.length > 5
        }?.let {
            result.add(it)
        }

        // éæ­¸å­ç¯é»
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractTextsRecursive(child, result, depth + 1)
                child.recycle()
            }
        }
    }

    // ââââââââââââââââââââââââââââââââââââââ
    // æ ¸å¿èçæµç¨
    // ââââââââââââââââââââââââââââââââââââââ

    private fun processInterceptedText(text: String, source: InterceptSource) {
        val now = System.currentTimeMillis()

        // é²éè¤ï¼ç¸åæå­ 2 ç§å§åªèçä¸æ¬¡
        if (text == lastProcessedText && (now - lastProcessedTime) < DEBOUNCE_MS) {
            return
        }

        lastProcessedText = text
        lastProcessedTime = now

        Log.d(TAG, "æªç²æå­ [${source.name}]: ${text.take(50)}...")

        serviceScope.launch {
            // 1. éç¥é·ééå§ãæèã
            FloatingWindowService.updateState(
                this@ThunderAccessibilityService,
                FloatingWindowService.STATE_THINKING
            )

            // 2. ç¨ ThunderPersona è½è­¯æå»£æ±è©±
            val thunderResponse = persona.translate(text, source)

            // 3. é¡¯ç¤ºè½è­¯çµæ
            FloatingWindowService.sendMessage(
                this@ThunderAccessibilityService,
                thunderResponse,
                text
            )

            Log.d(TAG, "é·éè¼¸åº: $thunderResponse")
        }
    }

    enum class InterceptSource {
        GALAXY_AI_SUMMARY,
        TRANSLATION,
        NOTIFICATION,
        UNKNOWN
    }
}
