package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class ModuleDetailActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private lateinit var content: LinearLayout
    private var moduleName = "Module"
    private fun isEn() = LocaleHelper.get(this) == "en"
    private fun t(sq: String, en: String) = if (isEn()) en else sq

    private fun row(heading: String, desc: String, onTap: (() -> Unit)?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 24)
        val a = TextView(this)
        a.text = heading
        a.textSize = 17f
        val b = TextView(this)
        b.text = desc
        b.textSize = 14f
        box.addView(a)
        box.addView(b)
        if (onTap != null) box.setOnClickListener { onTap() }
        content.addView(box)
    }

    private fun askChange(param: String, oldV: String, newV: String) {
        AlertDialog.Builder(this)
            .setTitle(t("Ndrysho funksionin?", "Change function?"))
            .setMessage(t(
                param + ": " + oldV + " → " + newV + "\n\nPara ndryshimit aplikacioni ruan automatikisht vlerën e vjetër te Backup. Nëse backup dështon, ndryshimi anulohet.",
                param + ": " + oldV + " → " + newV + "\n\nBefore the change the app automatically saves the old value to Backup. If the backup fails, the change is cancelled."))
            .setPositiveButton(t("Vazhdo", "Continue")) { _, _ -> runChange(param, oldV, newV) }
            .setNegativeButton(t("Anulo", "Cancel"), null)
            .show()
    }

    private fun runChange(param: String, oldV: String, newV: String) {
        Thread {
            val res = SafeWrite.change(this, moduleName, param, oldV, newV) { _ -> false }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setMessage(res.message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }

    private fun render(expert: Boolean) {
        content.removeAllViews()
        if (expert) {
            row("DEMO_PARAM_1", "nicht_aktiv", null)
            row("DEMO_PARAM_2", "aktiv", null)
            row("DEMO_PARAM_3", "0x01", null)
            row("DEMO_PARAM_4", "0xFF", null)
        } else {
            row(t("Funksioni demo 1", "Demo function 1"), t("Joaktiv", "Off")) {
                askChange("DEMO_1", "off", "on")
            }
            row(t("Funksioni demo 2", "Demo function 2"), t("Aktiv", "On")) {
                askChange("DEMO_2", "on", "off")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleName = intent.getStringExtra("name") ?: "Module"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.parseColor("#2F9BE0"))
        bar.setPadding(32, 96, 32, 32)

        val back = TextView(this)
        back.text = "←"
        back.textSize = 28f
        back.setTextColor(Color.WHITE)
        back.setPadding(0, 0, 32, 0)
        back.setOnClickListener { finish() }
        bar.addView(back)

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        val tt = TextView(this)
        tt.text = moduleName
        tt.textSize = 20f
        tt.setTextColor(Color.WHITE)
        val st = TextView(this)
        st.text = "Demo"
        st.setTextColor(Color.WHITE)
        titles.addView(tt)
        titles.addView(st)
        bar.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val hist = TextView(this)
        hist.text = "⟲"
        hist.textSize = 28f
        hist.setTextColor(Color.WHITE)
        hist.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
        bar.addView(hist)
        root.addView(bar)

        val sw = Switch(this)
        sw.text = "Expert mode"
        sw.textSize = 18f
        sw.setPadding(48, 32, 48, 32)
        root.addView(sw)

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        var guard = false
        sw.setOnCheckedChangeListener { _, checked ->
            if (guard) return@setOnCheckedChangeListener
            if (checked) {
                AlertDialog.Builder(this)
                    .setTitle("Expert mode")
                    .setMessage(t(
                        "Ky modalitet tregon parametra të papërpunuar. Ndryshimi i gabuar mund ta dëmtojë modulin. Para çdo ndryshimi aplikacioni bën backup. Vazhdo?",
                        "This mode shows raw parameters. A wrong change can damage the module. The app makes a backup before every change. Continue?"))
                    .setPositiveButton(t("Vazhdo", "Continue")) { _, _ -> render(true) }
                    .setNegativeButton(t("Anulo", "Cancel")) { _, _ ->
                        guard = true
                        sw.isChecked = false
                        guard = false
                    }
                    .setCancelable(false)
                    .show()
            } else {
                render(false)
            }
        }
        render(false)
        setContentView(root)
    }
}
