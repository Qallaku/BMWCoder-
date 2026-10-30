package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class BackupActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var list: ListView
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 24)

        val title = TextView(this)
        title.text = t("Backup (demo)", "Backup (demo)")
        title.textSize = 26f
        root.addView(title)

        val create = Button(this)
        create.text = t("Krijo backup provë", "Create test backup")
        create.setOnClickListener {
            BackupStore.save(this, "Front Electronic Module", "DEMO-00112233445566")
            refresh()
        }
        root.addView(create)

        val export = Button(this)
        export.text = t("Eksporto të gjitha", "Export all")
        export.setOnClickListener { exportAll() }
        root.addView(export)

        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos < files.size) show(files[pos])
        }
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        refresh()
    }

    private fun refresh() {
        files = BackupStore.list(this)
        val names = files.map { it.name.removeSuffix(".txt") }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun show(f: File) {
        AlertDialog.Builder(this)
            .setTitle(f.name.removeSuffix(".txt"))
            .setMessage(BackupStore.read(f))
            .setPositiveButton(t("Rikthe", "Restore")) { _, _ ->
                Toast.makeText(this,
                    t("Demo: rikthimi do funksionojë me makinën", "Demo: restore will work with the car"),
                    Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(t("Fshi", "Delete")) { _, _ ->
                BackupStore.delete(f)
                refresh()
            }
            .setNeutralButton(t("Mbyll", "Close"), null)
            .show()
    }

    private fun exportAll() {
        if (files.isEmpty()) {
            Toast.makeText(this, t("Nuk ka backup", "No backups"), Toast.LENGTH_SHORT).show()
            return
        }
        val text = files.joinToString("\n----\n") { it.name + "\n" + BackupStore.read(it) }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(i, t("Eksporto backup", "Export backup")))
    }
}package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun isEn() = LocaleHelper.get(this) == "en"
    private fun t(sq: String, en: String) = if (isEn()) en else sq

    private lateinit var title: TextView
    private lateinit var list: ListView
    private var current: String? = null
    private var mods: List<String> = emptyList()
    private var items: List<BackupStore.Item> = emptyList()
    private val demoMods = listOf("Front Electronic Module", "Headunit")

    private fun fmt(ms: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale(LocaleHelper.get(this))).format(Date(ms))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 24)

        title = TextView(this)
        title.textSize = 24f
        root.addView(title)

        val create = Button(this)
        create.text = t("Krijo backup provë", "Create test backup")
        create.setOnClickListener {
            val m = current ?: demoMods[((System.currentTimeMillis() / 1000) % 2).toInt()]
            BackupStore.save(this, m, "DEMO-" + System.currentTimeMillis() % 100000)
            refresh()
        }
        root.addView(create)

        val export = Button(this)
        export.text = t("Eksporto të gjitha", "Export all")
        export.setOnClickListener { exportAll() }
        root.addView(export)

        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ ->
            if (current == null) {
                if (pos < mods.size) {
                    current = mods[pos]
                    refresh()
                }
            } else {
                if (pos < items.size) show(items[pos])
            }
        }
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val cur = current
        if (cur == null) {
            title.text = "Backup"
            mods = BackupStore.modules(this)
            val names = mods.map { it + " (" + BackupStore.forModule(this, it).size + ")" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        } else {
            title.text = cur
            items = BackupStore.forModule(this, cur)
            val names = items.map {
                (if (it.original) "★ " + t("Origjinal", "Original") + "  " else "") + fmt(it.time)
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        }
    }

    private fun show(it: BackupStore.Item) {
        val msg = it.module + "\n" + fmt(it.time) + "\n\n" + it.data
        AlertDialog.Builder(this)
            .setTitle(if (it.original) "★ " + t("Origjinal", "Original") else fmt(it.time))
            .setMessage(msg)
            .setPositiveButton(t("Rikthe", "Restore")) { _, _ ->
                Toast.makeText(this,
                    t("Demo: rikthimi punon me makinën", "Demo: restore works with the car"),
                    Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(t("Fshi", "Delete")) { _, _ ->
                if (BackupStore.delete(it)) {
                    refresh()
                } else {
                    Toast.makeText(this,
                        t("Kopja origjinale nuk fshihet", "The original copy cannot be deleted"),
                        Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton(t("Mbyll", "Close"), null)
            .show()
    }

    private fun exportAll() {
        val all = BackupStore.all(this)
        if (all.isEmpty()) {
            Toast.makeText(this, t("Nuk ka backup", "No backups"), Toast.LENGTH_SHORT).show()
            return
        }
        val text = all.joinToString("\n----\n") {
            (if (it.original) "[ORIGINAL] " else "") + it.module + " | " + fmt(it.time) + " | " + it.data
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(i, t("Eksporto backup", "Export backup")))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current != null) {
            current = null
            refresh()
        } else {
            super.onBackPressed()
        }
    }
}
