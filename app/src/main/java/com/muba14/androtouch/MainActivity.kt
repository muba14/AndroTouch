package com.muba14.androtouch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muba14.androtouch.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val styleTiles = mutableMapOf<ButtonStyle, Pair<View, TouchButtonView>>()
    private val colorTiles = mutableMapOf<ButtonColor, Pair<View, TouchButtonView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        applyInsets()

        buildStyleOptions()
        buildColorOptions()

        b.switchEnabled.isChecked = prefs.enabled
        b.switchEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            if (checked && !AndroTouchService.isEnabledInSettings(this)) showServiceDialog()
        }

        b.btnService.setOnClickListener {
            if (AndroTouchService.isEnabledInSettings(this)) openAccessibilitySettings() else showServiceDialog()
        }
        b.btnAppInfo.setOnClickListener { openAppInfo() }

        b.sliderSize.value = snap(prefs.sizeDp, Prefs.MIN_SIZE_DP, 4)
        b.sliderSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.sizeDp = value.roundToInt()
            b.sizeValue.text = getString(R.string.size_value, value.roundToInt())
        }
        b.sizeValue.text = getString(R.string.size_value, prefs.sizeDp)

        b.sliderOpacity.value = snap(prefs.idleOpacity, 20, 10)
        b.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.idleOpacity = value.roundToInt()
            b.opacityValue.text = getString(R.string.opacity_value, value.roundToInt())
        }
        b.opacityValue.text = getString(R.string.opacity_value, prefs.idleOpacity)

        b.btnEditMenu.setOnClickListener {
            startActivity(Intent(this, MenuEditorActivity::class.java))
        }

        refreshAppearance()
    }

    override fun onResume() {
        super.onResume()
        refreshServiceStatus()
        refreshMenuSummary()
        refreshGestures()
    }

    // ---------------------------------------------------------------- Durum

    private fun refreshServiceStatus() {
        val on = AndroTouchService.isEnabledInSettings(this)
        b.serviceStatus.setText(if (on) R.string.service_on else R.string.service_off)
        b.serviceStatus.setTextColor(getColor(if (on) R.color.status_on else R.color.status_off))
        b.btnService.setText(if (on) R.string.service_settings else R.string.service_open)
        b.restrictedBox.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun showServiceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_service_title)
            .setMessage(R.string.dialog_service_message)
            .setPositiveButton(R.string.dialog_go_settings) { _, _ -> openAccessibilitySettings() }
            .setNeutralButton(R.string.open_app_info) { _, _ -> openAppInfo() }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun openAppInfo() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    // ---------------------------------------------------------------- Görünüm

    private fun buildStyleOptions() {
        ButtonStyle.entries.forEach { style ->
            val tile = makeTile(getString(style.label)) { prefs.style = style; refreshAppearance() }
            tile.second.buttonStyle = style
            styleTiles[style] = tile
            b.styleOptions.addView(tile.first)
        }
    }

    private fun buildColorOptions() {
        ButtonColor.entries.forEach { color ->
            val tile = makeTile(getString(color.label)) { prefs.color = color; refreshAppearance() }
            tile.second.buttonColor = color
            colorTiles[color] = tile
            b.colorOptions.addView(tile.first)
        }
    }

    /** Lacivert seçim kutusu: içinde buton önizlemesi, altında etiket. */
    private fun makeTile(label: String, onClick: () -> Unit): Pair<View, TouchButtonView> {
        val preview = TouchButtonView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_option)
            setPadding(dp(8), dp(14), dp(8), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
            addView(preview)
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(getColor(R.color.white))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            })
        }
        return tile to preview
    }

    private fun refreshAppearance() {
        val style = prefs.style
        val color = prefs.color
        b.previewButton.buttonStyle = style
        b.previewButton.buttonColor = color
        styleTiles.forEach { (s, tile) ->
            tile.first.isSelected = s == style
            tile.second.buttonColor = color
        }
        colorTiles.forEach { (c, tile) ->
            tile.first.isSelected = c == color
            tile.second.buttonStyle = style
        }
    }

    private fun refreshMenuSummary() {
        val entries = prefs.menuEntries
        b.menuCount.text = getString(R.string.menu_count, entries.size)
        b.menuIcons.removeAllViews()
        entries.forEach { entry ->
            b.menuIcons.addView(ImageView(this).apply {
                setImageDrawable(entry.icon(this@MainActivity))
                if (entry is MenuEntry.Action) {
                    setBackgroundResource(R.drawable.bg_icon_circle)
                    val pad = dp(6)
                    setPadding(pad, pad, pad, pad)
                }
                contentDescription = entry.label(this@MainActivity)
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(6) }
            })
        }
    }

    // ---------------------------------------------------------------- Hareketler

    private fun refreshGestures() {
        b.gestureRows.removeAllViews()
        Gesture.entries.forEach { g -> b.gestureRows.addView(buildGestureRow(g)) }
        val menuReachable = Gesture.entries.any { prefs.gesture(it) == GestureTarget.OpenMenu }
        b.gestureWarning.visibility = if (menuReachable) View.GONE else View.VISIBLE
    }

    /** "Tek dokunuş — Menüyü aç  ›" satırı. */
    private fun buildGestureRow(g: Gesture): View {
        val target = prefs.gesture(g)
        val rowPad = dp(8)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(0, rowPad, 0, rowPad)
            background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                .let { a -> a.getDrawable(0).also { a.recycle() } }
            isClickable = true
            isFocusable = true
            setOnClickListener { showGestureChooser(g) }

            addView(ImageView(this@MainActivity).apply {
                setImageResource(g.icon)
                setBackgroundResource(R.drawable.bg_icon_circle)
                val pad = dp(9)
                setPadding(pad, pad, pad, pad)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(12) }
                addView(TextView(this@MainActivity).apply {
                    setText(g.label)
                    setTextColor(getColor(R.color.text_primary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                })
                addView(TextView(this@MainActivity).apply {
                    text = target.label(this@MainActivity)
                    setTextColor(getColor(R.color.text_secondary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            })
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron)
                setColorFilter(getColor(R.color.text_secondary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            contentDescription = "${getString(g.label)}: ${target.label(this@MainActivity)}"
        }
    }

    /** Seçenek listesi: Menüyü aç, Hiçbir şey, (atanmış uygulama), sistem eylemleri, Uygulama seç… */
    private fun showGestureChooser(g: Gesture) {
        val current = prefs.gesture(g)
        val options = mutableListOf<GestureTarget?>(GestureTarget.OpenMenu, GestureTarget.None)
        if (current is GestureTarget.Run && current.entry is MenuEntry.App) options += current
        TouchAction.entries.filter { it.isSupported && it != TouchAction.OPEN_SETTINGS }
            .forEach { options += GestureTarget.Run(MenuEntry.Action(it)) }
        options += null // "Uygulama seç…"

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = options.size
            override fun getItem(position: Int) = options[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val vb = com.muba14.androtouch.databinding.ItemAppBinding.inflate(layoutInflater, parent, false)
                val opt = options[position]
                val iconView = vb.appIcon
                when {
                    opt == null -> setCircleIcon(iconView, R.drawable.ic_apps)
                    opt == GestureTarget.OpenMenu -> setCircleIcon(iconView, R.drawable.ic_menu_grid)
                    opt == GestureTarget.None -> setCircleIcon(iconView, R.drawable.ic_none)
                    opt is GestureTarget.Run && opt.entry is MenuEntry.App ->
                        iconView.setImageDrawable(opt.entry.icon(this@MainActivity))
                    opt is GestureTarget.Run -> {
                        iconView.setImageDrawable(opt.entry.icon(this@MainActivity))
                        iconView.setBackgroundResource(R.drawable.bg_icon_circle)
                        val pad = dp(9)
                        iconView.setPadding(pad, pad, pad, pad)
                    }
                }
                vb.appName.text = opt?.label(this@MainActivity) ?: getString(R.string.target_pick_app)
                vb.inMenu.setText(R.string.target_selected)
                vb.inMenu.visibility = if (opt != null && opt.id == current.id) View.VISIBLE else View.GONE
                return vb.root
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(g.label)
            .setAdapter(adapter) { _, which ->
                val chosen = options[which]
                if (chosen == null) {
                    AppPicker.show(this) { app -> applyGesture(g, GestureTarget.Run(MenuEntry.App(app.packageName))) }
                } else {
                    applyGesture(g, chosen)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun applyGesture(g: Gesture, target: GestureTarget) {
        prefs.setGesture(g, target)
        refreshGestures()
    }

    private fun setCircleIcon(view: ImageView, icon: Int) {
        view.setImageResource(icon)
        view.setBackgroundResource(R.drawable.bg_icon_circle)
        val pad = dp(9)
        view.setPadding(pad, pad, pad, pad)
    }

    // ---------------------------------------------------------------- Yardımcılar

    private fun applyInsets() {
        val headerTop = b.header.paddingTop
        val contentBottom = b.content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.header.updatePadding(top = headerTop + bars.top)
            b.content.updatePadding(bottom = contentBottom + bars.bottom)
            insets
        }
    }

    /** Slider değeri adımla uyumlu olmazsa çöker; değeri en yakın adıma yuvarlar. */
    private fun snap(value: Int, from: Int, step: Int): Float =
        (from + ((value - from).toFloat() / step).roundToInt() * step).toFloat()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
