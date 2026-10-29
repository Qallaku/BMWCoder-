package com.bmwcoder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout

class MenuActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val en = LocaleHelper.get(this) == "en"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        fun add(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { action() }
            root.addView(b)
        }

        add(if (en) "Connect to car" else "Lidhja me makinën") {
            startActivity(Intent(this, MainActivity::class.java))
        }
        add(if (en) "Modules (ECU)" else "Modulet (ECU)") {
            startActivity(Intent(this, ModulesActivity::class.java))
        }
        add("Backup") {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        add("Shqip") {
            LocaleHelper.set(this, "sq")
            recreate()
        }
        add("English") {
            LocaleHelper.set(this, "en")
            recreate()
        }
        setContentView(root)
    }
}
