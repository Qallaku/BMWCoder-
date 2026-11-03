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

    private lateinit var heading: TextView
    private lateinit var list: ListView
    private var current: String? = null
    private var mods: List<String> = emptyList()
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 24)

        heading = TextView(this)
        heading.textSize = 24f
        root.addView(heading)

        val create = Button(this)
        create.text = t("Krijo backup provë", "Create test backup")
        create.setOnClickListener {
            val m = current ?: "Front Electronic Module"
            BackupStore.save(this, m, "DEMO-" + (System.currentTimeMillis() % 100000))
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
                if (pos < files.size) show(files[pos])
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
            heading.text = "Backup"
            mods = BackupStore.modules(this)
            val names = mods.map { m -> m + " (" + BackupStore.forModule(this, m).size + ")" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        } else {
            heading.text = cur
            files = BackupStore.forModule(this, cur)
            val lang = LocaleHelper.get(this)
            val names = files.map { f ->
                val tag = if (BackupStore.isOriginal(this, f)) "★ " + t("Origjinal", "Original") + "  " else ""
                tag + BackupStore.pretty(f, lang)
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        }
    }

    private fun show(f: File) {
        val orig = BackupStore.isOriginal(this, f)
        val ttl = if (orig) "★ " + t("Origjinal", "Original") else BackupStore.pretty(f, LocaleHelper.get(this))
        AlertDialog.Builder(this)
            .setTitle(ttl)
            .setMessage(BackupStore.read(f))
            .setPositiveButton(t("Rikthe", "Restore")) { _, _ ->
                Toast.makeText(this,
                    t("Rikthimi do aktivizohet me shkrimin në makinë.", "Restore will be enabled together with writing to the car."),
                    Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(t("Fshi", "Delete")) { _, _ ->
                if (BackupStore.deleteSafe(this, f)) {
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
        val all = BackupStore.list(this)
        if (all.isEmpty()) {
            Toast.makeText(this, t("Nuk ka backup", "No backups"), Toast.LENGTH_SHORT).show()
            return
        }
        val text = all.joinToString("\n----\n") { f -> f.name + "\n" + BackupStore.read(f) }
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
