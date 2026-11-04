package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
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

class DtcActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private val logBuf = StringBuilder()
    private var faulty = LinkedHashMap<String, List<String>>()
    private var scanned = false
    @Volatile private var busy = false
    @Volatile private var stopFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val head = TextView(this)
        head.text = t("Gabimet (DTC)", "Fault codes (DTC)")
        head.textSize = 26f
        root.addView(head)

        fun add(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { action() }
            root.addView(b)
        }

        add(t("Lexo gabimet e të gjitha moduleve (BMW)", "Read faults of all modules (BMW)")) { scanAll() }
        add(t("Lexo gabimet e motorit (OBD-II)", "Read engine faults (OBD-II)")) { readObd() }
        add(t("Pastro gabimet e gjetura", "Clear found faults")) { confirmClear() }
        add(t("Ndalo", "Stop")) { stopFlag = true }

        out = TextView(this)
        out.textSize = 15f
        scroll = ScrollView(this)
        scroll.addView(out)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        add(t("Kopjo raportin", "Copy report")) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", logBuf.toString()))
            Toast.makeText(this, t("U kopjua", "Copied"), Toast.LENGTH_SHORT).show()
        }
        setContentView(root)
        log(t("Lidhu te Lidhja me makinën, ndiz motorin, pastaj shtyp Lexo. Pa lidhje punon në demo.",
            "Connect first, start the engine, then tap Read. Without a connection it runs in demo mode."))
    }

    private fun log(msg: String) {
        runOnUiThread {
            logBuf.append(msg).append("\n")
            out.text = logBuf.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun clean(s: String) = s.replace("\r", " ").replace("\n", " ").trim()

    private fun hex(v: Int) = String.format("%02X", v)

    private fun restoreAdapter() {
        CarLink.send("ATD", 2000)
        CarLink.send("ATE0", 2000)
        CarLink.send("ATL0", 2000)
        CarLink.send("ATH0", 2000)
        CarLink.send("ATSP6", 3000)
    }

    private fun prepareBmw() {
        CarLink.send("ATD", 2000)
        CarLink.send("ATE0", 2000)
        CarLink.send("ATL0", 2000)
        CarLink.send("ATSP6", 3000)
        CarLink.send("ATH1", 2000)
        CarLink.send("ATCAF0", 2000)
        CarLink.send("ATST 30", 2000)
        CarLink.send("ATSH 6F1", 2000)
        CarLink.send("ATCM 700", 2000)
        CarLink.send("ATCF 600", 2000)
    }

    private fun isHex(s: String) = s.all { ch -> ch.isDigit() || ch in 'A'..'F' }

    private fun frames(r: String): List<List<String>> {
        val res = ArrayList<List<String>>()
        for (ln in r.split("\r", "\n")) {
            val p = ln.trim().split(" ").filter { it.isNotEmpty() }
            if (p.size >= 3 && p[0].length == 3 && p[0][0] == '6' && p[1] == "F1" &&
                p.all { isHex(it) }
            ) {
                res.add(p)
            }
        }
        return res
    }

    private fun fromAddr(f: List<String>, addr: String) = f[0].substring(1) == addr

    private fun uds(addr: String, req: String): List<Int>? {
        val toks = req.trim().split(" ")
        val n = toks.size
        val sb = StringBuilder()
        sb.append(addr).append(' ').append(hex(n)).append(' ').append(req.trim())
        for (i in 0 until (6 - n)) sb.append(" 00")
        val r = CarLink.send(sb.toString(), 1200)
        val fr = frames(r).filter { fromAddr(it, addr) }
        if (fr.isEmpty()) return null
        val first = fr[0]
        val pci = first[2].toInt(16)
        val res = ArrayList<Int>()
        if ((pci and 0xF0) == 0x00) {
            val len = pci and 0x0F
            for (i in 0 until len) {
                if (3 + i < first.size) res.add(first[3 + i].toInt(16))
            }
        } else if ((pci and 0xF0) == 0x10 && first.size >= 5) {
            val total = ((pci and 0x0F) shl 8) or first[3].toInt(16)
            for (i in 4 until first.size) res.add(first[i].toInt(16))
            val fc = CarLink.send(addr + " 30 00 00 00 00 00 00", 2000)
            val more = fr.drop(1) + frames(fc).filter { fromAddr(it, addr) }
            for (f in more) {
                val p = f[2].toInt(16)
                if ((p and 0xF0) == 0x20) {
                    for (i in 3 until f.size) res.add(f[i].toInt(16))
                }
            }
            while (res.size > total) res.removeAt(res.size - 1)
        }
        return res
    }

    private fun udsRetry(addr: String, req: String): List<Int>? {
        var p = uds(addr, req)
        var tries = 0
        while (p != null && p.size >= 3 && p[0] == 0x7F && p[2] == 0x78 && tries < 3) {
            Thread.sleep(400)
            p = uds(addr, req)
            tries++
        }
        return p
    }

    private fun parseFaults(p: List<Int>): List<String> {
        val res = ArrayList<String>()
        if (p.size >= 3 && p[0] == 0x59) {
            var i = 3
            while (i + 3 < p.size) {
                res.add(String.format("%02X%02X%02X [%02X]", p[i], p[i + 1], p[i + 2], p[i + 3]))
                i += 4
            }
        }
        return res
    }

    private fun clearModule(addr: String): Boolean {
        val r = CarLink.send(addr + " 04 14 FF FF FF 00 00", 1500)
        return frames(r).any { fromAddr(it, addr) && it.size >= 4 && it[3] == "54" }
    }

    private fun scanAll() {
        if (!CarLink.isConnected) {
            faulty = linkedMapOf("12" to listOf("8030A2 [2F]"))
            scanned = true
            logBuf.setLength(0)
            log("DEMO (" + t("pa lidhje me makinën", "not connected") + "):")
            log("0x12: 1 " + t("gabim", "fault") + "  8030A2 [2F]")
            return
        }
        if (busy) return
        busy = true
        stopFlag = false
        logBuf.setLength(0)
        log(t("Po skanoj, rreth 1-2 minuta ...", "Scanning, about 1-2 minutes ..."))
        Thread {
            try {
                log(t("Tensioni: ", "Voltage: ") + clean(CarLink.send("ATRV", 2000)))
                prepareBmw()
                val found = ArrayList<String>()
                for (a in 0..255) {
                    if (stopFlag) break
                    val addr = hex(a)
                    val r = CarLink.send(addr + " 02 3E 00 00 00 00 00", 600)
                    if (frames(r).any { fromAddr(it, addr) }) found.add(addr)
                    if (a % 64 == 63) log("... " + (a + 1) + "/256")
                }
                log(t("U gjetën module: ", "Modules found: ") + found.size)
                val result = LinkedHashMap<String, List<String>>()
                var okCount = 0
                var refused = 0
                var silent = 0
                for (addr in found) {
                    if (stopFlag) break
                    val p = udsRetry(addr, "19 02 FF")
                    if (p == null) {
                        silent++
                        log("0x" + addr + ": " + t("pa përgjigje", "no reply"))
                    } else if (p.isNotEmpty() && p[0] == 0x7F) {
                        refused++
                        log("0x" + addr + ": " + t("refuzoi", "refused") + " (" + p.joinToString(" ") { hex(it) } + ")")
                    } else {
                        okCount++
                        val d = parseFaults(p)
                        if (d.isEmpty()) {
                            log("0x" + addr + ": OK, 0 " + t("gabime", "faults"))
                        } else {
                            result[addr] = d
                            log("0x" + addr + ": " + d.size + " " + t("gabime", "faults") + "  " + d.joinToString(", "))
                        }
                    }
                }
                restoreAdapter()
                faulty = result
                scanned = true
                log(t("Mbaroi. U lexuan: ", "Done. Read: ") + okCount +
                    t(", refuzuan: ", ", refused: ") + refused +
                    t(", pa përgjigje: ", ", no reply: ") + silent)
                log(t("Module me gabime: ", "Modules with faults: ") + result.size)
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun decode(b1: Int, b2: Int): String {
        val letters = "PCBU"
        val l = letters[(b1 shr 6) and 3]
        val d1 = (b1 shr 4) and 3
        val d2 = b1 and 15
        return "" + l + d1 + Integer.toHexString(d2).uppercase() + String.format("%02X", b2)
    }

    private fun parseObd(resp: String): List<String> {
        val hexs = StringBuilder()
        for (line in resp.split("\r", "\n")) {
            var l = line.trim().replace(" ", "")
            if (l.isEmpty()) continue
            if (l.contains(":")) l = l.substring(l.indexOf(":") + 1)
            if (l.length == 3) continue
            if (!isHex(l)) continue
            hexs.append(l)
        }
        val h = hexs.toString()
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

    private fun readObd() {
        if (!CarLink.isConnected) {
            logBuf.setLength(0)
            log("DEMO: P0133, U0100")
            return
        }
        if (busy) return
        busy = true
        logBuf.setLength(0)
        log(t("Po lexoj gabimet e motorit (OBD-II) ...", "Reading engine faults (OBD-II) ..."))
        Thread {
            try {
                restoreAdapter()
                val r = CarLink.send("03", 8000)
                val codes = parseObd(r)
                if (codes.isEmpty()) {
                    log(t("Nuk ka gabime të ruajtura (OBD-II).", "No stored faults (OBD-II)."))
                } else {
                    log(codes.joinToString("\n"))
                }
                log("raw: " + clean(r))
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun confirmClear() {
        if (!scanned || faulty.isEmpty()) {
            Toast.makeText(this,
                t("Lexo gabimet fillimisht (ose nuk ka gabime).", "Read faults first (or there are none)."),
                Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(t("Pastro gabimet?", "Clear faults?"))
            .setMessage(t(
                "Do pastrohen gabimet në " + faulty.size + " module. Para çdo pastrimi aplikacioni ruan listën te Backup dhe kontrollon tensionin (min. 12.5 V). Gabimi mund të kthehet nëse defekti vazhdon. Vazhdo?",
                "Faults will be cleared in " + faulty.size + " modules. Before each clear the app saves the list to Backup and checks voltage (min. 12.5 V). The fault may return if the defect remains. Continue?"))
            .setPositiveButton(t("Vazhdo", "Continue")) { _, _ -> doClear() }
            .setNegativeButton(t("Anulo", "Cancel"), null)
            .show()
    }

    private fun doClear() {
        if (busy) return
        busy = true
        log(t("Po pastroj ...", "Clearing ..."))
        Thread {
            try {
                if (CarLink.isConnected) prepareBmw()
                for ((addr, codes) in faulty) {
                    val old = codes.joinToString(" ")
                    val res = SafeWrite.change(this, "DTC " + addr, "CLEAR", old, "none") { _ ->
                        if (!CarLink.isConnected) false else clearModule(addr)
                    }
                    log("0x" + addr + ": " + res.message)
                }
                if (CarLink.isConnected) restoreAdapter()
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }
}
