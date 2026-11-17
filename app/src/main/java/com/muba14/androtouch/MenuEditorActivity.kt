package com.muba14.androtouch

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.muba14.androtouch.databinding.ActivityMenuEditorBinding
import com.muba14.androtouch.databinding.ItemActionBinding
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Menüdeki öğeleri seçme ve sıralama ekranı.
 * Listenin sırası menünün sırasıdır; işaretli olanlar menüde görünür.
 * "Uygulama ekle" ile telefondaki herhangi bir uygulama menüye eklenebilir.
 * Her değişiklik anında kaydedilir ve açık olan yüzen buton hemen günceller.
 */
class MenuEditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityMenuEditorBinding
    private lateinit var prefs: Prefs
    private val items = mutableListOf<Item>()
    private lateinit var adapter: ActionAdapter
    private lateinit var touchHelper: ItemTouchHelper

    data class Item(val entry: MenuEntry, var checked: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        b = ActivityMenuEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        val listBottom = b.list.paddingBottom
        val fabMargin = dp(16)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.toolbar.updatePadding(top = bars.top)
            b.list.updatePadding(bottom = listBottom + bars.bottom)
            b.fabAddApp.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = fabMargin + bars.bottom }
            insets
        }

        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_editor)
        b.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_reset) {
                prefs.resetMenu()
                reloadList()
                true
            } else false
        }

        loadItems()
        adapter = ActionAdapter()
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        touchHelper = ItemTouchHelper(DragCallback())
        touchHelper.attachToRecyclerView(b.list)

        b.fabAddApp.setOnClickListener { showAppPicker() }
        // Liste kaydırılınca buton küçülüp yalnızca + simgesi kalsın.
        b.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 4) b.fabAddApp.shrink() else if (dy < -4) b.fabAddApp.extend()
            }
        })

        refreshHeader()
    }

    private fun loadItems() {
        items.clear()
        val selected = prefs.menuEntries
        selected.forEach { items += Item(it, true) }
        val selectedIds = selected.map { it.id }.toSet()
        TouchAction.entries
            .filter { it.isSupported && it.id !in selectedIds }
            .forEach { items += Item(MenuEntry.Action(it), false) }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadList() {
        loadItems()
        adapter.notifyDataSetChanged()
        refreshHeader()
    }

    private fun checkedCount() = items.count { it.checked }

    private fun save() {
        prefs.menuEntries = items.filter { it.checked }.map { it.entry }
        refreshHeader()
    }

    private fun refreshHeader() {
        val selected = items.filter { it.checked }.map { it.entry }
        b.info.text = getString(R.string.editor_info, selected.size, TouchAction.MAX_IN_MENU)
        buildPreview(selected)
    }

    /** Ekrandaki menü panelinin küçük bir kopyası. */
    private fun buildPreview(entries: List<MenuEntry>) {
        val grid = b.previewGrid
        grid.removeAllViews()
        grid.columnCount = when (entries.size) {
            1 -> 1
            2, 4 -> 2
            else -> 3
        }
        entries.forEach { entry ->
            val iconSize = if (entry is MenuEntry.App) dp(26) else dp(22)
            grid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(66)
                    height = dp(60)
                }
                addView(ImageView(this@MenuEditorActivity).apply {
                    setImageDrawable(entry.icon(this@MenuEditorActivity))
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                })
                addView(TextView(this@MenuEditorActivity).apply {
                    text = entry.label(this@MenuEditorActivity)
                    setTextColor(getColor(R.color.white))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                })
            })
        }
    }

    // ---------------------------------------------------------------- Uygulama seçici

    private fun showAppPicker() {
        if (checkedCount() >= TouchAction.MAX_IN_MENU) {
            Toast.makeText(this, getString(R.string.editor_max, TouchAction.MAX_IN_MENU), Toast.LENGTH_SHORT).show()
            return
        }
        val inMenu = items.filter { it.checked }.map { it.entry.id }.toSet()
        AppPicker.show(this, inMenu) { app -> addApp(app) }
    }

    private fun addApp(app: InstalledApp) {
        val entry = MenuEntry.App(app.packageName)
        val existing = items.indexOfFirst { it.entry.id == entry.id }
        if (existing != -1 && items[existing].checked) {
            Toast.makeText(this, getString(R.string.app_already, app.label), Toast.LENGTH_SHORT).show()
            return
        }
        if (checkedCount() >= TouchAction.MAX_IN_MENU) {
            Toast.makeText(this, getString(R.string.editor_max, TouchAction.MAX_IN_MENU), Toast.LENGTH_SHORT).show()
            return
        }
        if (existing != -1) {
            items.removeAt(existing)
            adapter.notifyItemRemoved(existing)
        }
        // İşaretli öğelerin sonuna ekle, böylece menüde en sonda görünür.
        val insertAt = items.indexOfLast { it.checked } + 1
        items.add(insertAt, Item(entry, true))
        adapter.notifyItemInserted(insertAt)
        b.list.scrollToPosition(insertAt)
        save()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    // ---------------------------------------------------------------- Liste

    private inner class Holder(val vb: ItemActionBinding) : RecyclerView.ViewHolder(vb.root)

    private inner class ActionAdapter : RecyclerView.Adapter<Holder>() {

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val holder = Holder(ItemActionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            attachDragHandle(holder)
            holder.vb.root.setOnClickListener { holder.vb.check.toggle() }
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val label = item.entry.label(this@MenuEditorActivity)
            holder.vb.icon.setImageDrawable(item.entry.icon(this@MenuEditorActivity))
            if (item.entry is MenuEntry.App) {
                // Uygulamalar kendi renkli ikonuyla, lacivert daire olmadan görünür.
                holder.vb.icon.background = null
                holder.vb.icon.setPadding(0, 0, 0, 0)
            } else {
                holder.vb.icon.setBackgroundResource(R.drawable.bg_icon_circle)
                val pad = dp(9)
                holder.vb.icon.setPadding(pad, pad, pad, pad)
            }
            holder.vb.label.text = label
            holder.vb.check.setOnCheckedChangeListener(null)
            holder.vb.check.isChecked = item.checked
            holder.vb.check.contentDescription = label
            holder.vb.root.alpha = if (item.checked) 1f else 0.6f
            holder.vb.check.setOnCheckedChangeListener { button, checked ->
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                val selectedCount = checkedCount()
                when {
                    checked && selectedCount >= TouchAction.MAX_IN_MENU -> {
                        button.isChecked = false
                        Toast.makeText(this@MenuEditorActivity,
                            getString(R.string.editor_max, TouchAction.MAX_IN_MENU), Toast.LENGTH_SHORT).show()
                    }
                    !checked && selectedCount <= 1 -> {
                        button.isChecked = true
                        Toast.makeText(this@MenuEditorActivity, R.string.editor_min, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        items[pos].checked = checked
                        holder.vb.root.alpha = if (checked) 1f else 0.6f
                        save()
                    }
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun attachDragHandle(holder: Holder) {
            holder.vb.handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
                false
            }
        }
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun isLongPressDragEnabled() = true

        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val z = to.bindingAdapterPosition
            if (a == RecyclerView.NO_POSITION || z == RecyclerView.NO_POSITION) return false
            Collections.swap(items, a, z)
            adapter.notifyItemMoved(a, z)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.animate()?.scaleX(1.03f)?.scaleY(1.03f)?.setDuration(120)?.start()
            }
        }

        override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(rv, viewHolder)
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            save() // bırakınca yeni sırayı kaydet
        }
    }
}
