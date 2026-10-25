package com.example.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object TvUtils {
    /**
     * Checks if the device running the app is an Android TV / Google TV.
     */
    fun isRunningOnTv(context: Context): Boolean {
        return try {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true
            }
            val pm = context.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television")
        } catch (_: Exception) {
            false
        }
    }
}
