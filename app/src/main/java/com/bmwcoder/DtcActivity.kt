package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DtcActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var out: TextView
    private var lastCodes: List<String> = emptyList()
    private var readDone = false
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val head = TextView(this)
        head.text = t("Gabimet (DTC)", "Fault codes (DTC)")
        head.textSize = 26f
        root.addView(head)

        val read = Button(this)
        read.text = t("Lexo gabimet", "Read faults")
        read.setOnClickListener { readFaults() }
        root.addView(read)

        val clear = Button(this)
        clear.text = t("Pastro gabimet", "Clear faults")
        clear.setOnClickListener { confirmClear() }
        root.addView(clear)

        out = TextView(this)
        out.textSize = 16f
        val sc = ScrollView(this)
        sc.addView(out)
        root.addView(sc, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        show(t("Shtyp Lexo gabimet. Pa lidhje me makinën punon në modalitet demo.",
            "Tap Read faults. Without a car connection it runs in demo mode."))
    }

    private fun show(msg: String) {
        runOnUiThread { out.text = msg }
    }

    private fun prepare() {
        CarLink.send("ATAR", 2000)
        CarLink.send("ATH0", 2000)
        CarLink.send("ATCAF1", 2000)
        CarLink.send("ATSH 7DF", 2000)
    }

    private fun decode(b1: Int, b2: Int): String {
        val letters = "PCBU"
        val l = letters[(b1 shr 6) and 3]
        val d1 = (b1 shr 4) and 3
        val d2 = b1 and 15
        return "" + l + d1 + Integer.toHexString(d2).uppercase() + String.format("%02X", b2)
    }

    private fun parse(resp: String): List<String> {
        val hex = StringBuilder()
        for (line in resp.split("\r", "\n")) {
            var l = line.trim().replace(" ", "")
            if (l.isEmpty()) continue
            if (l.contains(":")) l = l.substring(l.indexOf(":") + 1)
            if (l.length == 3) continue
            if (!l.all { c -> c.isDigit() || c in 'A'..'F' }) continue
            hex.append(l)
        }
        val h = hex.toString()
        val i = h.indexOf("43")
        if (i < 0 || h.length < i + 4) return emptyList()
        val count = h.substring(i + 2, i + 4).toInt(16)
        val codes = ArrayList<String>()
        var p = i + 4
        var n = 0
        while (n < count && p + 4 <= h.length) {
            val b1 = h.substring(p, p + 2).toInt(16)
            val b2 = h.substring(p + 2, p + 4).toInt(16)
            if (b1 != 0 || b2 != 0) codes.add(decode(b1, b2))
            p += 4
            n++
        }
        return codes
    }

    private fun readFaults() {
        if (!CarLink.isConnected) {
            lastCodes = listOf("P0133", "U0100")
            readDone = true
            show(t("DEMO (pa lidhje me makinën):\n", "DEMO (not connected):\n") +
                lastCodes.joinToString("\n"))
            return
        }
        if (busy) return
        busy = true
        show(t("Po lexoj ...", "Reading ..."))
        Thread {
            try {
                prepare()
                val r = CarLink.send("03", 8000)
                val codes = parse(r)
                lastCodes = codes
                readDone = true
                val msg = if (codes.isEmpty()) {
                    t("Nuk ka gabime të ruajtura.", "No stored faults.")
                } else {
                    codes.joinToString("\n")
                }
                show(msg + "\n\nraw: " + r.replace("\r", " ").replace("\n", " "))
            } catch (e: Exception) {
                show(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun confirmClear() {
        if (!readDone) {
            Toast.makeText(this,
                t("Lexo gabimet para pastrimit.", "Read the faults before clearing."),
                Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(t("Pastro gabimet?", "Clear faults?"))
            .setMessage(t(
                "Para pastrimit aplikacioni ruan listën e gabimeve te Backup. Pastrimi fshin edhe të dhënat e freeze-frame dhe i rivendos monitorët e emetimeve. Gabimi mund të kthehet nëse defekti vazhdon. Vazhdo?",
                "Before clearing, the app saves the fault list to Backup. Clearing also erases freeze-frame data and resets the emission monitors. The fault may return if the defect remains. Continue?"))
            .setPositiveButton(t("Vazhdo", "Continue")) { _, _ -> doClear() }
            .setNegativeButton(t("Anulo", "Cancel"), null)
            .show()
    }

    private fun doClear() {
        val snapshot = if (lastCodes.isEmpty()) "none" else lastCodes.joinToString(" | ")
        BackupStore.save(this, "DTC", "FAULTS " + snapshot)
        if (!CarLink.isConnected) {
            show(t("DEMO: lista u ruajt te Backup. Asgjë nuk u dërgua në makinë.",
                "DEMO: the list was saved to Backup. Nothing was sent to the car."))
            return
        }
        if (busy) return
        busy = true
        show(t("Po pastroj ...", "Clearing ..."))
        Thread {
            try {
                prepare()
                val r = CarLink.send("04", 8000)
                val ok = r.replace(" ", "").contains("44")
                show(if (ok) {
                    t("Gabimet u pastruan. Lista e vjetër është te Backup.",
                        "Faults cleared. The old list is in Backup.")
                } else {
                    t("Pastrimi nuk u konfirmua. Përgjigja: ", "Clear not confirmed. Reply: ") +
                        r.replace("\r", " ").replace("\n", " ")
                })
            } catch (e: Exception) {
                show(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }
}
