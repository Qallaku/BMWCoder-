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
}
