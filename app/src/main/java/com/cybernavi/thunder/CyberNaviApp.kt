package com.cybernavi.thunder

import android.app.Application

/**
 * CyberNaviApp — 全局 Application 實例
 *
 * 職責：
 *  1. 提供全局 Context，供 SettingsStore DataStore 使用
 *  2. 作為 companion object 靜態入口，方便各模組存取
 *
 * 在 AndroidManifest.xml 的 <application> 標籤加入：
 *   android:name=".CyberNaviApp"
 */
class CyberNaviApp : Application() {

    companion object {
        lateinit var instance: CyberNaviApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
