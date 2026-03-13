# CyberNavi ProGuard Rules
# Phase 1 — 保持調試信息方便排查問題

# 保留所有類名（調試用）
-keepattributes SourceFile,LineNumberTable
-keep class com.cybernavi.thunder.** { *; }

# 保留 Accessibility Service
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# 保留 Service / Receiver
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
