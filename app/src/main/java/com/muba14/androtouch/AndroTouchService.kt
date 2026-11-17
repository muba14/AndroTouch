package com.muba14.androtouch

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * AndroTouch'un çalışan kısmı.
 *  - Yüzen butonu ve menüyü ekrana çizer (erişilebilirlik katmanı sayesinde ayrıca
 *    "diğer uygulamaların üzerinde göster" izni gerekmez).
 *  - Geri, ana ekran, kilit, ekran görüntüsü gibi sistem eylemlerini çalıştırır.
 * Uygulama ekrandaki içerikleri OKUMAZ; yalnızca eylem gönderir.
 */
class AndroTouchService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var floating: FloatingController
    private lateinit var torch: TorchHelper
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        torch = TorchHelper(this)
        floating = FloatingController(this, prefs) { perform(it) }
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        applyEnabled()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::floating.isInitialized) floating.onScreenChanged()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_ENABLED -> applyEnabled()
            Prefs.KEY_STYLE, Prefs.KEY_COLOR, Prefs.KEY_SIZE, Prefs.KEY_IDLE_OPACITY -> floating.refreshAppearance()
        }
    }

    private fun applyEnabled() {
        if (prefs.enabled) floating.show() else floating.hide()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    private fun cleanup() {
        if (instance === this) instance = null
        if (::prefs.isInitialized) prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        if (::floating.isInitialized) floating.hide()
        if (::torch.isInitialized) torch.release()
        handler.removeCallbacksAndMessages(null)
    }

    /** Menüden seçilen öğeyi çalıştırır: uygulamaysa açar, eylemse yapar. */
    fun perform(entry: MenuEntry) {
        when (entry) {
            is MenuEntry.Action -> perform(entry.action)
            is MenuEntry.App -> if (!InstalledApps.launch(this, entry.packageName)) {
                Toast.makeText(this, getString(R.string.action_failed, entry.label(this)), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun perform(action: TouchAction) {
        val ok: Boolean = when (action) {
            TouchAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            TouchAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            TouchAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            TouchAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            TouchAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            TouchAction.POWER -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            TouchAction.SPLIT_SCREEN -> performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            TouchAction.LOCK ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            TouchAction.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) false else {
                    // Buton ekran görüntüsünde çıkmasın diye kısa süre gizlenir.
                    floating.hideButtonFor(SCREENSHOT_HIDE_MS)
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }, SCREENSHOT_DELAY_MS)
                    true
                }
            }
            TouchAction.FLASHLIGHT -> torch.toggle()
            TouchAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            TouchAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            TouchAction.MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            TouchAction.OPEN_SETTINGS -> {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }
        if (!ok) Toast.makeText(this, getString(R.string.action_failed, getString(action.label)), Toast.LENGTH_SHORT).show()
    }

    private fun adjustVolume(direction: Int): Boolean = runCatching {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }.isSuccess

    companion object {
        private const val SCREENSHOT_DELAY_MS = 350L
        private const val SCREENSHOT_HIDE_MS = 1500L

        @Volatile
        var instance: AndroTouchService? = null
            private set

        /** Kullanıcı Ayarlar > Erişilebilirlik'ten AndroTouch'u açmış mı? */
        fun isEnabledInSettings(context: Context): Boolean {
            val expected = ComponentName(context, AndroTouchService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
