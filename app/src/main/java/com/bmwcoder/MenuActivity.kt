package com.bmwcoder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MenuActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun isEn() = LocaleHelper.get(this) == "en"
    private fun t(sq: String, en: String) = if (isEn()) en else sq

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val title = TextView(this)
        title.text = "BmwCoder"
        title.textSize = 30f
        root.addView(        val b3 = Button(this)
        b3.text = "Backup"
        b3.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
        root.addView(b3))

        val sub = TextView(this)
        sub.text = t("Zgjidh një opsion", "Choose an option")
        sub.textSize = 16f
        sub.setPadding(0, 0, 0, 32)
        root.addView(sub)

        val b1 = Button(this)
        b1.text = t("Lidhja me makinën", "Connect to car")
        b1.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        root.addView(b1)

        val b2 = Button(this)
        b2.text = t("Modulet (ECU)", "Modules (ECU)")
        b2.setOnClickListener { startActivity(Intent(this, ModulesActivity::class.java)) }
        root.addView(b2)

        val langLabel = TextView(this)
        langLabel.text = "Gjuha / Language"
        langLabel.textSize = 16f
        langLabel.setPadding(0, 48, 0, 8)
        root.addView(langLabel)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        val sq = Button(this)
        sq.text = "Shqip"
        sq.setOnClickListener {
            LocaleHelper.set(this, "sq")
            recreate()
        }
        row.addView(sq, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val en = Button(this)
        en.text = "English"
        en.setOnClickListener {
            LocaleHelper.set(this, "en")
            recreate()
        }
        row.addView(en, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(row)
        setContentView(root)
    }
}
