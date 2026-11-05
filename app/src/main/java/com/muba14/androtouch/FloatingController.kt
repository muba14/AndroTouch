package com.muba14.androtouch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Yüzen butonu ve dokununca açılan menü panelini yönetir.
 *  - Sürüklenebilir, bırakınca en yakın kenara yapışır, konumu hatırlanır.
 *  - Bir süre dokunulmazsa ayarlardaki saydamlığa kadar soluklaşır.
 *  - Tek dokunuş, çift dokunuş ve uzun basma ayarlardan atanan işi yapar
 *    (varsayılan: tek dokunuş menüyü açar).
 */
class FloatingController(
    private val context: Context,
    private val prefs: Prefs,
    private val onAction: (MenuEntry) -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var button: TouchButtonView? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var menu: FrameLayout? = null
    private var snapAnimator: ValueAnimator? = null

    private val fadeRunnable = Runnable {
        button?.animate()?.alpha(prefs.idleOpacity / 100f)?.setDuration(FADE_MS)?.start()
    }

    // ---------------------------------------------------------------- Buton

    fun show() {
        if (button != null) return
        val sizePx = dp(prefs.sizeDp)
        val view = TouchButtonView(context).apply {
            buttonStyle = prefs.style
            buttonColor = prefs.color
            contentDescription = context.getString(R.string.app_name)
        }
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        button = view
        buttonParams = params
        placeFromPrefs()
        view.setOnTouchListener(DragListener())
        runCatching { wm.addView(view, params) }.onFailure { button = null; buttonParams = null; return }
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(FADE_MS).start()
        scheduleFade()
    }

    fun hide() {
        closeMenu(animated = false)
        handler.removeCallbacksAndMessages(null)
        snapAnimator?.cancel()
        button?.let { runCatching { wm.removeView(it) } }
        button = null
        buttonParams = null
    }

    /** Stil, renk, boyut veya saydamlık değişince çağrılır. */
    fun refreshAppearance() {
        val view = button ?: return
        val params = buttonParams ?: return
        view.buttonStyle = prefs.style
        view.buttonColor = prefs.color
        val sizePx = dp(prefs.sizeDp)
        params.width = sizePx
        params.height = sizePx
        placeFromPrefs()
        runCatching { wm.updateViewLayout(view, params) }
        view.animate().cancel()
        view.alpha = 1f
        scheduleFade()
    }

    /** Ekran döndüğünde veya boyutu değiştiğinde butonu doğru kenara geri koyar. */
    fun onScreenChanged() {
        closeMenu(animated = false)
        val view = button ?: return
        placeFromPrefs()
        runCatching { wm.updateViewLayout(view, buttonParams) }
    }

    /** Ekran görüntüsü alınırken butonu kısa süreliğine gizler. */
    fun hideButtonFor(ms: Long) {
        val view = button ?: return
        view.visibility = View.INVISIBLE
        handler.postDelayed({ button?.visibility = View.VISIBLE }, ms)
    }

    private fun placeFromPrefs() {
        val params = buttonParams ?: return
        val screen = screenSize()
        val margin = dp(EDGE_MARGIN_DP)
        params.x = if (prefs.onRightEdge) screen.x - params.width - margin else margin
        val maxY = (screen.y - params.height).coerceAtLeast(0)
        params.y = (prefs.verticalFraction * maxY).roundToInt()
    }

    private fun snapToEdge() {
        val view = button ?: return
        val params = buttonParams ?: return
        val screen = screenSize()
        val margin = dp(EDGE_MARGIN_DP)
        val toRight = params.x + params.width / 2 > screen.x / 2
        val targetX = if (toRight) screen.x - params.width - margin else margin
        val maxY = (screen.y - params.height).coerceAtLeast(1)
        params.y = params.y.coerceIn(0, maxY)

        prefs.onRightEdge = toRight
        prefs.verticalFraction = params.y.toFloat() / maxY

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_MS
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (button != null) runCatching { wm.updateViewLayout(view, params) }
            }
            start()
        }
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fadeRunnable)
        handler.postDelayed(fadeRunnable, IDLE_DELAY_MS)
    }

    /** Bir hareketin işini yapar. */
    private fun runGesture(gesture: Gesture) {
        when (val target = prefs.gesture(gesture)) {
            GestureTarget.OpenMenu -> openMenu()
            GestureTarget.None -> scheduleFade()
            is GestureTarget.Run -> {
                onAction(target.entry)
                scheduleFade()
            }
        }
    }

    /**
     * Sürükleme + hareket algılama.
     *  - Parmak kayarsa: sürükleme (hiçbir hareket tetiklenmez).
     *  - Kaymadan basılı tutulursa: uzun basma (titreşimle).
     *  - Bırakılırsa: çift dokunuşa bir iş atanmışsa kısa süre ikinci dokunuş beklenir,
     *    gelmezse tek dokunuş çalışır. Atanmamışsa tek dokunuş hemen çalışır.
     */
    private inner class DragListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private var waitingSecondTap = false
        private var touchedView: View? = null

        private val longPressRunnable = Runnable {
            longPressed = true
            waitingSecondTap = false // ikinci basış uzun basmaya dönüştü, çift dokunuş sayılmaz
            touchedView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            touchedView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(PRESS_MS)?.start()
            runGesture(Gesture.LONG_PRESS)
        }

        private val singleTapRunnable = Runnable {
            waitingSecondTap = false
            runGesture(Gesture.TAP)
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val params = buttonParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(fadeRunnable)
                    snapAnimator?.cancel()
                    v.animate().cancel()
                    v.alpha = 1f
                    v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(PRESS_MS).start()
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    touchedView = v
                    // İkinci dokunuş başladı: tek dokunuş zamanlayıcısını durdur, bırakınca çift dokunuş sayılır.
                    if (waitingSecondTap) handler.removeCallbacks(singleTapRunnable)
                    if (prefs.gesture(Gesture.LONG_PRESS) != GestureTarget.None) {
                        handler.postDelayed(longPressRunnable, longPressTimeout)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressed) return true
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                        cancelPendingTap()
                    }
                    if (dragging) {
                        val screen = screenSize()
                        params.x = (startX + dx).roundToInt().coerceIn(0, screen.x - params.width)
                        params.y = (startY + dy).roundToInt().coerceIn(0, screen.y - params.height)
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS).start()
                    when {
                        dragging -> {
                            snapToEdge()
                            scheduleFade()
                        }
                        longPressed -> Unit // uzun basmanın işi zaten yapıldı
                        e.actionMasked == MotionEvent.ACTION_CANCEL -> scheduleFade()
                        else -> {
                            v.performClick()
                            onTapReleased()
                        }
                    }
                }
            }
            return true
        }

        private fun onTapReleased() {
            if (waitingSecondTap) {
                cancelPendingTap()
                runGesture(Gesture.DOUBLE_TAP)
            } else if (prefs.gesture(Gesture.DOUBLE_TAP) != GestureTarget.None) {
                waitingSecondTap = true
                handler.postDelayed(singleTapRunnable, doubleTapTimeout)
            } else {
                runGesture(Gesture.TAP)
            }
        }

        private fun cancelPendingTap() {
            waitingSecondTap = false
            handler.removeCallbacks(singleTapRunnable)
        }
    }

    // ---------------------------------------------------------------- Menü

    private fun openMenu() {
        if (menu != null) return
        val entries = prefs.menuEntries
        handler.removeCallbacks(fadeRunnable)
        button?.visibility = View.INVISIBLE

        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    closeMenu(animated = true)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(0x66000000)
            isFocusableInTouchMode = true
            setOnClickListener { closeMenu(animated = true) } // boşluğa dokununca kapan
        }

        val panel = buildPanel(entries)
        root.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (runCatching { wm.addView(root, params) }.isFailure) {
            button?.visibility = View.VISIBLE
            return
        }
        menu = root
        root.requestFocus()

        root.alpha = 0f
        root.animate().alpha(1f).setDuration(MENU_ANIM_MS).start()
        panel.scaleX = 0.6f
        panel.scaleY = 0.6f
        panel.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(MENU_ANIM_MS).start()
    }

    private fun buildPanel(entries: List<MenuEntry>): View {
        val columns = when (entries.size) {
            1 -> 1
            2, 4 -> 2
            else -> 3
        }
        val grid = GridLayout(context).apply {
            columnCount = columns
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(context.getColor(R.color.menu_panel))
            }
            isClickable = true // panelin içine dokunmak menüyü kapatmasın
            elevation = dp(8).toFloat()
        }
        entries.forEach { entry -> grid.addView(buildItem(entry)) }
        return grid
    }

    private fun buildItem(entry: MenuEntry): View {
        val label = entry.label(context)
        val iconSize = if (entry is MenuEntry.App) dp(36) else dp(30)
        val cell = dp(88)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = cell
                height = cell
            }
            background = context.getDrawable(R.drawable.bg_menu_item)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener {
                closeMenu(animated = false)
                // Menü penceresi kapandıktan sonra eylemi çalıştır.
                handler.postDelayed({ onAction(entry) }, ACTION_DELAY_MS)
            }

            addView(ImageView(context).apply {
                setImageDrawable(entry.icon(context))
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            })
            addView(TextView(context).apply {
                text = label
                setTextColor(context.getColor(R.color.menu_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                setPadding(dp(4), 0, dp(4), 0)
            })
        }
    }

    private fun closeMenu(animated: Boolean) {
        val root = menu ?: return
        menu = null
        val finish = {
            runCatching { wm.removeView(root) }
            button?.let {
                it.visibility = View.VISIBLE
                it.alpha = 1f
            }
            scheduleFade()
        }
        if (animated) {
            root.animate().alpha(0f).setDuration(MENU_ANIM_MS)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = finish()
                }).start()
        } else {
            root.animate().cancel()
            finish()
        }
    }

    // ---------------------------------------------------------------- Yardımcılar

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            Point().also { wm.defaultDisplay.getRealSize(it) }
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val EDGE_MARGIN_DP = 6
        const val IDLE_DELAY_MS = 2500L
        const val FADE_MS = 300L
        const val SNAP_MS = 280L
        const val PRESS_MS = 90L
        const val PRESS_SCALE = 0.9f
        const val MENU_ANIM_MS = 180L
        const val ACTION_DELAY_MS = 120L
    }
}
