package com.bmwcoder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MenuActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private lateinit var status: TextView

    private fun en() = LocaleHelper.get(this) == "en"
    private fun t(sq: String, e: String) = if (en()) e else sq
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun cardBg(): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.WHITE)
        g.cornerRadius = dp(14).toFloat()
        g.setStroke(dp(1), Color.parseColor("#D5DDE5"))
        return g
    }

    private fun item(list: LinearLayout, icon: String, title: String, desc: String, target: Class<*>) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = cardBg()
        row.setPadding(dp(16), dp(14), dp(16), dp(14))

        val ic = TextView(this)
        ic.text = icon
        ic.textSize = 28f
        ic.setPadding(0, 0, dp(16), 0)
        row.addView(ic)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val a = TextView(this)
        a.text = title
        a.textSize = 17f
        a.setTextColor(Color.parseColor("#1B2733"))
        a.setTypeface(null, Typeface.BOLD)
        val b = TextView(this)
        b.text = desc
        b.textSize = 13f
        b.setTextColor(Color.parseColor("#5B6B7A"))
        col.addView(a)
        col.addView(b)
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.setOnClickListener { startActivity(Intent(this, target)) }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(16), dp(6), dp(16), dp(6))
        list.addView(row, lp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Color.parseColor("#F2F5F8"))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(Color.parseColor("#2F9BE0"))
        header.setPadding(dp(20), dp(44), dp(20), dp(20))

        val badge = TextView(this)
        badge.text = "Semi"
        badge.textSize = 20f
        badge.gravity = Gravity.CENTER
        badge.setTextColor(Color.parseColor("#2F9BE0"))
        badge.setTypeface(null, Typeface.BOLD)
        val bd = GradientDrawable()
        bd.shape = GradientDrawable.OVAL
        bd.setColor(Color.WHITE)
        bd.setStroke(dp(3), Color.parseColor("#0B4F84"))
        badge.background = bd
        header.addView(badge, LinearLayout.LayoutParams(dp(64), dp(64)))

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(dp(16), 0, 0, 0)
        val title = TextView(this)
        title.text = "BmwCoder"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        status = TextView(this)
        status.textSize = 14f
        status.setTextColor(Color.WHITE)
        val credit = TextView(this)
        credit.text = t("Ndërtuar me ndihmën e Claude", "Built with help from Claude")
        credit.textSize = 12f
        credit.setTextColor(Color.parseColor("#CFE8FA"))
        titles.addView(title)
        titles.addView(status)
        titles.addView(credit)
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(header)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, dp(10), 0, dp(10))
        item(list, "🔌", t("Lidhja me makinën", "Connect to car"),
            t("Lidhu me adapterin Bluetooth", "Connect to the Bluetooth adapter"),
            MainActivity::class.java)
        item(list, "📡", t("Skano makinën", "Scan car"),
            t("Gjej modulet në makinë", "Find the modules in the car"),
            ScanActivity::class.java)
        item(list, "⚠", t("Gabimet (DTC)", "Fault codes (DTC)"),
            t("Lexo dhe pastro gabimet", "Read and clear faults"),
            DtcActivity::class.java)
        item(list, "📈", t("Të dhëna live", "Live data"),
            t("Rrotullime, temperatura, tension", "RPM, temperatures, voltage"),
            LiveActivity::class.java)
        item(list, "🔍", t("Lexim i modulit", "Module read"),
            t("Identifikimi dhe kodimi, vetëm lexim", "Identification and coding, read only"),
            ReadActivity::class.java)
        item(list, "🧩", t("Modulet (ECU)", "Modules (ECU)"),
            t("Funksionet e moduleve", "Module functions"),
            ModulesActivity::class.java)
        item(list, "💾", "Backup",
            t("Kopjet dhe historiku sipas modulit", "Copies and history per module"),
            BackupActivity::class.java)

        val sc = ScrollView(this)
        sc.addView(list)
        page.addView(sc, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val langRow = LinearLayout(this)
        langRow.orientation = LinearLayout.HORIZONTAL
        langRow.setPadding(dp(16), dp(8), dp(16), dp(16))
        val sq = Button(this)
        sq.text = "Shqip"
        sq.setOnClickListener {
            LocaleHelper.set(this, "sq")
            recreate()
        }
        langRow.addView(sq, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val enBtn = Button(this)
        enBtn.text = "English"
        enBtn.setOnClickListener {
            LocaleHelper.set(this, "en")
            recreate()
        }
        langRow.addView(enBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(langRow)

        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (CarLink.isConnected) {
            t("● Lidhur me ", "● Connected to ") + CarLink.deviceName
        } else {
            t("○ Pa lidhje me makinën", "○ Not connected")
        }
    }
}
