package com.example.util

import android.content.res.Resources
import android.os.Build
import java.util.Locale

object LocaleUtils {
    /**
     * Safely resolves the target locale without throwing IndexOutOfBoundsException or
     * NoSuchFieldError across all Android API levels, even if the system locale list is empty.
     */
    fun getEffectiveLocale(targetLocale: Locale?): Locale {
        if (targetLocale != null) return targetLocale

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val locales = Resources.getSystem().configuration.locales
                if (!locales.isEmpty && locales.size() > 0) {
                    locales.get(0) ?: Locale.getDefault()
                } else {
                    Locale.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale ?: Locale.getDefault()
            }
        } catch (_: Throwable) {
            Locale.getDefault()
        }
    }
}
