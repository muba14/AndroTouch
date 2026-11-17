package com.muba14.androtouch

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Menüye eklenebilecek tüm eylemler. [id] ayarlarda saklanır, değiştirme. */
enum class TouchAction(
    val id: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    private val minSdk: Int = Build.VERSION_CODES.O
) {
    BACK("back", R.string.action_back, R.drawable.ic_back),
    HOME("home", R.string.action_home, R.drawable.ic_home),
    RECENTS("recents", R.string.action_recents, R.drawable.ic_recents),
    NOTIFICATIONS("notifications", R.string.action_notifications, R.drawable.ic_notifications),
    QUICK_SETTINGS("quick_settings", R.string.action_quick_settings, R.drawable.ic_quick_settings),
    LOCK("lock", R.string.action_lock, R.drawable.ic_lock, Build.VERSION_CODES.P),
    SCREENSHOT("screenshot", R.string.action_screenshot, R.drawable.ic_screenshot, Build.VERSION_CODES.P),
    POWER("power", R.string.action_power, R.drawable.ic_power),
    FLASHLIGHT("flashlight", R.string.action_flashlight, R.drawable.ic_flashlight),
    VOLUME_UP("volume_up", R.string.action_volume_up, R.drawable.ic_volume_up),
    VOLUME_DOWN("volume_down", R.string.action_volume_down, R.drawable.ic_volume_down),
    MUTE("mute", R.string.action_mute, R.drawable.ic_volume_off),
    SPLIT_SCREEN("split_screen", R.string.action_split, R.drawable.ic_split),
    OPEN_SETTINGS("open_settings", R.string.action_open_settings, R.drawable.ic_settings);

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    companion object {
        const val MAX_IN_MENU = 9

        val DEFAULT_MENU = listOf(NOTIFICATIONS, HOME, QUICK_SETTINGS, BACK, RECENTS, SCREENSHOT, LOCK, FLASHLIGHT)

        fun fromId(id: String): TouchAction? = entries.firstOrNull { it.id == id }
    }
}
