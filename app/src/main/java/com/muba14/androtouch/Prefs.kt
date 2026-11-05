package com.muba14.androtouch

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Tüm kullanıcı ayarları tek yerde. Servis bu ayarları dinleyip butonu anında günceller. */
class Prefs(context: Context) {

    private val appContext: Context = context.applicationContext

    val sp: SharedPreferences =
        appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Kullanıcı yüzen butonu göstermek istiyor mu? (Servis açık değilse yine görünmez.) */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit { putBoolean(KEY_ENABLED, v) }

    var style: ButtonStyle
        get() = runCatching { ButtonStyle.valueOf(sp.getString(KEY_STYLE, null)!!) }.getOrDefault(ButtonStyle.SQUARE)
        set(v) = sp.edit { putString(KEY_STYLE, v.name) }

    var color: ButtonColor
        get() = runCatching { ButtonColor.valueOf(sp.getString(KEY_COLOR, null)!!) }.getOrDefault(ButtonColor.GRAY)
        set(v) = sp.edit { putString(KEY_COLOR, v.name) }

    /** Buton boyutu (dp). */
    var sizeDp: Int
        get() = sp.getInt(KEY_SIZE, DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
        set(v) = sp.edit { putInt(KEY_SIZE, v.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)) }

    /** Dokunulmadığında butonun görünürlüğü (yüzde, 20–100). */
    var idleOpacity: Int
        get() = sp.getInt(KEY_IDLE_OPACITY, 50).coerceIn(20, 100)
        set(v) = sp.edit { putInt(KEY_IDLE_OPACITY, v.coerceIn(20, 100)) }

    /** Menüdeki öğeler (eylemler ve uygulamalar), sırasıyla. */
    var menuEntries: List<MenuEntry>
        get() {
            val raw = sp.getString(KEY_MENU, null) ?: return defaultMenu()
            val list = raw.split(',').filter { it.isNotBlank() }
                .mapNotNull { MenuEntry.fromId(appContext, it) }
                .distinctBy { it.id }
            return list.ifEmpty { defaultMenu() }
        }
        set(v) = sp.edit { putString(KEY_MENU, v.take(TouchAction.MAX_IN_MENU).joinToString(",") { it.id }) }

    private fun defaultMenu(): List<MenuEntry> =
        TouchAction.DEFAULT_MENU.filter { it.isSupported }.map { MenuEntry.Action(it) }

    /** Bir harekete atanmış iş. Atanan uygulama kaldırılmışsa varsayılana döner. */
    fun gesture(g: Gesture): GestureTarget =
        sp.getString(g.prefKey, null)?.let { GestureTarget.fromId(appContext, it) } ?: g.default

    fun setGesture(g: Gesture, target: GestureTarget) = sp.edit { putString(g.prefKey, target.id) }

    /** Butonun konumu: hangi kenarda ve ekran yüksekliğinin yüzde kaçında. */
    var onRightEdge: Boolean
        get() = sp.getBoolean(KEY_RIGHT, true)
        set(v) = sp.edit { putBoolean(KEY_RIGHT, v) }

    var verticalFraction: Float
        get() = sp.getFloat(KEY_Y, 0.6f).coerceIn(0f, 1f)
        set(v) = sp.edit { putFloat(KEY_Y, v.coerceIn(0f, 1f)) }

    fun resetMenu() = sp.edit { remove(KEY_MENU) }

    companion object {
        const val FILE = "androtouch_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_STYLE = "style"
        const val KEY_COLOR = "color"
        const val KEY_SIZE = "size_dp"
        const val KEY_IDLE_OPACITY = "idle_opacity"
        const val KEY_MENU = "menu_actions"
        const val KEY_RIGHT = "on_right_edge"
        const val KEY_Y = "vertical_fraction"

        const val MIN_SIZE_DP = 40
        const val MAX_SIZE_DP = 80
        const val DEFAULT_SIZE_DP = 56
    }
}
