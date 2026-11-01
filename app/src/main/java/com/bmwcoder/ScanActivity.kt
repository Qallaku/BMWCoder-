package com.bmwcoder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ScanActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var info: TextView
    private lateinit var scroll: ScrollView
    private val logBuf = StringBuilder()
    @Volatile private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val head = TextView(this)
        head.text = t("Skano makinën", "Scan car")
        head.textSize = 26f
        root.addView(head)

        val go = Button(this)
        go.text = t("Fillo skanimin", "Start scan")
        go.setOnClickListener { startScan() }
        root.addView(go)

        info = TextView(this)
        info.textSize = 14f
        scroll = ScrollView(this)
        scroll.addView(info)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val copy = Button(this)
        copy.text = t("Kopjo raportin", "Copy report")
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", logBuf.toString()))
            Toast.makeText(this, t("U kopjua", "Copied"), Toast.LENGTH_SHORT).show()
        }
        root.addView(copy)

        setContentView(root)
        log(t("Para skanimit: lidhu te Lidhja me makinën dhe ndize kontaktin.",
            "Before scanning: connect in Connect to car and turn the ignition on."))
    }

    private fun log(msg: String) {
        runOnUiThread {
            logBuf.append(msg).append("\n")
            info.text = logBuf.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun clean(s: String) = s.replace("\r", " ").replace("\n", " ").trim()

    private fun isCanLine(s: String): Boolean {
        if (s.length < 8 || s[0] != '6') return false
        return s.all { ch -> ch.isDigit() || ch in 'A'..'F' || ch == ' ' }
    }

    private fun startScan() {
        if (!CarLink.isConnected) {
            log(t("Nuk ka lidhje. Shko te Lidhja me makinën dhe lidhu me adapterin.",
                "Not connected. Go to Connect to car first."))
            return
        }
        if (running) return
        running = true
        logBuf.setLength(0)
        log(t("Po skanoj, rreth 90 sekonda ...", "Scanning, about 90 seconds ..."))
        Thread {
            try {
                log(t("Tensioni: ", "Voltage: ") + clean(CarLink.send("ATRV", 2000)))
                CarLink.send("ATSP6", 3000)
                CarLink.send("ATH1", 2000)
                CarLink.send("ATCAF0", 2000)
                CarLink.send("ATST 40", 2000)
                CarLink.send("ATSH 6F1", 2000)
                CarLink.send("ATCM 700", 2000)
                CarLink.send("ATCF 600", 2000)
                log("ATDP: " + clean(CarLink.send("ATDP", 2000)))
                var count = 0
                for (a in 0..255) {
                    val addr = String.format("%02X", a)
                    val r = CarLink.send(addr + " 03 22 F1 90 00 00 00", 1500)
                    if (a == 0x10 || a == 0x12) {
                        log("raw 0x" + addr + ": " + clean(r))
                    }
                    val lines = r.split("\r", "\n").map { it.trim() }.filter { isCanLine(it) }
                    if (lines.isNotEmpty()) {
                        count++
                        log("0x" + addr + "  <-  " + lines[0])
                    }
                }
                CarLink.send("ATCAF1", 2000)
                CarLink.send("ATAR", 2000)
                CarLink.send("ATH0", 2000)
                CarLink.send("ATSH 7DF", 2000)
                log(t("Skanimi mbaroi. Module që u përgjigjën: ",
                    "Scan finished. Modules that answered: ") + count)
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                running = false
            }
        }.start()
    }
}
