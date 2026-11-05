package com.muba14.androtouch

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muba14.androtouch.databinding.DialogAppPickerBinding
import com.muba14.androtouch.databinding.ItemAppBinding
import java.util.concurrent.Executors

/**
 * Telefondaki uygulamaları aramalı bir listede gösteren pencere.
 * Menü düzenleyici ve hareket ayarları bunu ortak kullanır.
 */
object AppPicker {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Uygulama listesi bir kez yüklenir, sonraki açılışlar anında gelir. */
    @Volatile
    private var cache: List<InstalledApp>? = null

    /**
     * @param marked "Menüde" etiketiyle gösterilecek uygulamaların menü kimlikleri ("app:paket").
     */
    fun show(
        activity: AppCompatActivity,
        marked: Set<String> = emptySet(),
        onPick: (InstalledApp) -> Unit
    ) {
        val vb = DialogAppPickerBinding.inflate(activity.layoutInflater)
        lateinit var dialog: AlertDialog
        val adapter = Adapter(marked) { app ->
            dialog.dismiss()
            onPick(app)
        }
        vb.appList.layoutManager = LinearLayoutManager(activity)
        vb.appList.adapter = adapter

        fun applyFilter(query: String) {
            val all = cache ?: return
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
            adapter.submit(filtered)
            vb.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        vb.search.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.picker_title)
            .setView(vb.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()

        if (cache != null) {
            vb.progress.visibility = View.GONE
            applyFilter("")
        } else {
            val appContext = activity.applicationContext
            io.execute {
                val apps = InstalledApps.load(appContext)
                main.post {
                    cache = apps
                    if (activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return@post
                    vb.progress.visibility = View.GONE
                    applyFilter(vb.search.text?.toString().orEmpty())
                }
            }
        }
    }

    private class Holder(val vb: ItemAppBinding) : RecyclerView.ViewHolder(vb.root)

    private class Adapter(
        private val marked: Set<String>,
        private val onPick: (InstalledApp) -> Unit
    ) : RecyclerView.Adapter<Holder>() {
        private var data: List<InstalledApp> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<InstalledApp>) {
            data = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = data[position]
            holder.vb.appIcon.setImageDrawable(app.icon)
            holder.vb.appName.text = app.label
            val already = (MenuEntry.APP_PREFIX + app.packageName) in marked
            holder.vb.inMenu.visibility = if (already) View.VISIBLE else View.GONE
            holder.vb.root.alpha = if (already) 0.6f else 1f
            holder.vb.root.setOnClickListener { onPick(app) }
        }
    }
}
