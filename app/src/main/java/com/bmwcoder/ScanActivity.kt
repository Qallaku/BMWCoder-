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
import java.util.TreeSet

class ScanActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var info: TextView
    private lateinit var scroll: ScrollView
    private val logBuf = StringBuilder()
    @Volatile private var running = false
    @Volatile private var stopFlag = false

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

        val stop = Button(this)
        stop.text = t("Ndalo", "Stop")
        stop.setOnClickListener { stopFlag = true }
        root.addView(stop)

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
        log(t("Para skanimit: lidhu te Lidhja me makinën. Më mirë me motorin ndezur.",
            "Before scanning: connect in Connect to car. Best with the engine running."))
    }

    private fun log(msg: String) {
        runOnUiThread {
            logBuf.append(msg).append("\n")
            info.text = logBuf.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun clean(s: String) = s.replace("\r", " ").replace("\n", " ").trim()

    private fun responder(line: String): String? {
        val p = line.trim().split(" ").filter { it.isNotEmpty() }
        if (p.size < 3) return null
        val id = p[0]
        if (id.length != 3 || id[0] != '6') return null
        if (!id.all { ch -> ch.isDigit() || ch in 'A'..'F' }) return null
        if (p[1] != "F1") return null
        return id.substring(1)
    }

    private fun startScan() {
        if (!CarLink.isConnected) {
            log(t("Nuk ka lidhje. Shko te Lidhja me makinën dhe lidhu me adapterin.",
                "Not connected. Go to Connect to car first."))
            return
        }
        if (running) return
        running = true
        stopFlag = false
        logBuf.setLength(0)
        log(t("Po skanoj, rreth 40 sekonda ...", "Scanning, about 40 seconds ..."))
        Thread {
            try {
                log(t("Tensioni: ", "Voltage: ") + clean(CarLink.send("ATRV", 2000)))
                CarLink.send("ATSP6", 3000)
                CarLink.send("ATH1", 2000)
                CarLink.send("ATCAF0", 2000)
                CarLink.send("ATST 20", 2000)
                CarLink.send("ATSH 6F1", 2000)
                CarLink.send("ATCM 700", 2000)
                CarLink.send("ATCF 600", 2000)
                log("ATDP: " + clean(CarLink.send("ATDP", 2000)))
                val found = TreeSet<String>()
                for (a in 0..255) {
                    if (stopFlag) break
                    val addr = String.format("%02X", a)
                    val r = CarLink.send(addr + " 02 3E 00 00 00 00 00", 800)
                    for (ln in r.split("\r", "\n")) {
                        val who = responder(ln)
                        if (who != null && found.add(who)) {
                            log("0x" + who + "  <-  " + ln.trim())
                        }
                    }
                    if (a % 64 == 63) log("... " + (a + 1) + "/256")
                }
                CarLink.send("ATCAF1", 2000)
                CarLink.send("ATAR", 2000)
                CarLink.send("ATH0", 2000)
                CarLink.send("ATSH 7DF", 2000)
                log(t("Skanimi mbaroi. Module që u përgjigjën: ",
                    "Scan finished. Modules that answered: ") + found.size)
                log(found.joinToString(", ") { "0x$it" })
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                running = false
            }
        }.start()
    }
}
