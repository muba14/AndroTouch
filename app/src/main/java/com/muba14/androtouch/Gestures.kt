package com.muba14.androtouch

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Yüzen butona yapılabilen hareketler. */
enum class Gesture(
    val prefKey: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int
) {
    TAP("gesture_tap", R.string.gesture_tap, R.drawable.ic_touch),
    DOUBLE_TAP("gesture_double_tap", R.string.gesture_double_tap, R.drawable.ic_double_tap),
    LONG_PRESS("gesture_long_press", R.string.gesture_long_press, R.drawable.ic_long_press);

    /** İlk kurulumdaki atama. */
    val default: GestureTarget
        get() = when (this) {
            TAP -> GestureTarget.OpenMenu
            DOUBLE_TAP -> GestureTarget.Run(
                MenuEntry.Action(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) TouchAction.LOCK else TouchAction.BACK
                )
            )
            LONG_PRESS -> GestureTarget.None
        }
}

/** Bir harekete atanan iş: menüyü aç, hiçbir şey yapma ya da bir eylem/uygulama çalıştır. */
sealed class GestureTarget {
    abstract val id: String

    data object OpenMenu : GestureTarget() {
        override val id = "menu"
    }

    data object None : GestureTarget() {
        override val id = "none"
    }

    data class Run(val entry: MenuEntry) : GestureTarget() {
        override val id: String get() = entry.id
    }

    fun label(context: Context): String = when (this) {
        OpenMenu -> context.getString(R.string.target_menu)
        None -> context.getString(R.string.target_none)
        is Run -> entry.label(context)
    }

    companion object {
        /** Saklanan kimliği çözer; kaldırılmış uygulama gibi geçersiz bir değer null döner. */
        fun fromId(context: Context, id: String): GestureTarget? = when (id) {
            OpenMenu.id -> OpenMenu
            None.id -> None
            else -> MenuEntry.fromId(context, id)?.let { Run(it) }
        }
    }
}
