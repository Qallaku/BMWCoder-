package com.bmwcoder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MenuActivity : Activity() {
    private fun t(sq: String, en: String) =
        if (Locale.getDefault().language == "en") en else sq

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val title = TextView(this)
        title.text = "BmwCoder"
        title.textSize = 30f
        root.addView(title)

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

        setContentView(root)
    }
}
