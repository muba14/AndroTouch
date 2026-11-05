package com.muba14.androtouch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Menüdeki tek bir öğe: ya bir sistem eylemi ya da telefondaki bir uygulama.
 * [id] ayarlarda saklanır: eylemler için "home", uygulamalar için "app:com.ornek.paket".
 */
sealed class MenuEntry {
    abstract val id: String

    data class Action(val action: TouchAction) : MenuEntry() {
        override val id: String get() = action.id
    }

    data class App(val packageName: String) : MenuEntry() {
        override val id: String get() = APP_PREFIX + packageName
    }

    /** Menüde görünen ad. */
    fun label(context: Context): String = when (this) {
        is Action -> context.getString(action.label)
        is App -> runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    /** Uygulamalar kendi renkli ikonuyla görünür, eylemler beyaz çizgi ikonla. */
    fun icon(context: Context): Drawable? = when (this) {
        is Action -> context.getDrawable(action.icon)
        is App -> runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    companion object {
        const val APP_PREFIX = "app:"

        fun fromId(context: Context, id: String): MenuEntry? =
            if (id.startsWith(APP_PREFIX)) {
                val pkg = id.removePrefix(APP_PREFIX)
                if (isLaunchable(context, pkg)) App(pkg) else null // kaldırılmış uygulamalar atlanır
            } else {
                TouchAction.fromId(id)?.takeIf { it.isSupported }?.let { Action(it) }
            }

        fun isLaunchable(context: Context, pkg: String): Boolean =
            context.packageManager.getLaunchIntentForPackage(pkg) != null
    }
}

/** Telefondaki, ana ekrandan açılabilen bir uygulama (uygulama seçici listesi için). */
data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

object InstalledApps {
    /** Ana ekranda görünen uygulamaları ada göre sıralı döndürür. Yavaş olabilir; arka planda çağır. */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return activities
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    InstalledApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
