package com.bmwcoder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ReadActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var addrBox: EditText
    private lateinit var didBox: EditText
    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private val logBuf = StringBuilder()
    @Volatile private var busy = false
    @Volatile private var incomplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(48, 96, 48, 48)

        val head = TextView(this)
        head.text = t("Lexim i modulit (vetëm lexim)", "Module read (read only)")
        head.textSize = 24f
        col.addView(head)

        addrBox = EditText(this)
        addrBox.hint = t("Adresa e modulit, hex (p.sh. 12)", "Module address, hex (e.g. 12)")
        col.addView(addrBox)

        didBox = EditText(this)
        didBox.hint = t("DID, 4 shifra hex (p.sh. F190 ose 3000)", "DID, 4 hex digits (e.g. F190 or 3000)")
        col.addView(didBox)

        fun add(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { action() }
            col.addView(b)
        }

        add(t("1. Gjej modulet (22 F190)", "1. Find modules (22 F190)")) { findModules() }
        add(t("2. Lexo identifikimin e modulit", "2. Read module identification")) { readIdent() }
        add(t("3. Lexo këtë DID", "3. Read this DID")) { readOne() }
        add(t("4. Skano 256 DID nga ky fillim", "4. Scan 256 DIDs from this start")) { scanDids() }
        add(t("Foto A (para ndryshimit)", "Snapshot A (before change)")) { snap("A") }
        add(t("Foto B (pas ndryshimit)", "Snapshot B (after change)")) { snap("B") }
        add(t("Krahaso A me B", "Compare A with B")) { compare() }
        add(t("Ruaj raportin te Backup", "Save report to Backup")) { saveBackup() }
        add(t("Kopjo raportin", "Copy report")) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", logBuf.toString()))
            Toast.makeText(this, t("U kopjua", "Copied"), Toast.LENGTH_SHORT).show()
        }

        out = TextView(this)
        out.textSize = 14f
        out.setPadding(0, 24, 0, 0)
        col.addView(out)

        scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
        log(t("Lidhu fillimisht. Ky ekran dërgon vetëm komanda leximi. Pa lidhje punon në demo.",
            "Connect first. This screen only sends read commands. Without a connection it runs in demo mode."))
    }

    private fun log(msg: String) {
        runOnUiThread {
            logBuf.append(msg).append("\n")
            out.text = logBuf.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun hex(v: Int) = String.format("%02X", v)
    private fun isHex(s: String) = s.all { ch -> ch.isDigit() || ch in 'A'..'F' }

    private fun addr(): String? {
        val a = addrBox.text.toString().trim().uppercase()
        if (a.isEmpty() || a.length > 2 || !isHex(a)) {
            Toast.makeText(this, t("Adresa duhet 1-2 shifra hex.", "Address must be 1-2 hex digits."),
                Toast.LENGTH_LONG).show()
            return null
        }
        return a.padStart(2, '0')
    }

    private fun did(): String? {
        val d = didBox.text.toString().trim().uppercase()
        if (d.length != 4 || !isHex(d)) {
            Toast.makeText(this, t("DID duhet 4 shifra hex.", "DID must be 4 hex digits."),
                Toast.LENGTH_LONG).show()
            return null
        }
        return d
    }

    private fun voltInfo(): String {
        val v = CarLink.send("ATRV", 2000).trim()
        val m = Regex("([0-9]+\\.?[0-9]*)").find(v)
        val x = m?.groupValues?.get(1)?.toDoubleOrNull()
        return if (x != null && x < 12.0) {
            v + t(" (i ulët: modulet mund të mos përgjigjen)", " (low: modules may not respond)")
        } else {
            v
        }
    }

    private fun restore() {
        CarLink.send("ATD", 2000)
        CarLink.send("ATE0", 2000)
        CarLink.send("ATL0", 2000)
        CarLink.send("ATH0", 2000)
        CarLink.send("ATSP6", 3000)
    }

    private fun prepare(st: String) {
        CarLink.send("ATD", 2000)
        CarLink.send("ATE0", 2000)
        CarLink.send("ATL0", 2000)
        CarLink.send("ATSP6", 3000)
        CarLink.send("ATH1", 2000)
        CarLink.send("ATCAF0", 2000)
        CarLink.send("ATST " + st, 2000)
        CarLink.send("ATSH 6F1", 2000)
        CarLink.send("ATCM 700", 2000)
        CarLink.send("ATCF 600", 2000)
    }

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

    private fun fromAddr(f: List<String>, a: String) = f[0].substring(1) == a

    private fun readSvc(a: String, req: List<Int>): List<Int>? {
        if (req.isEmpty() || req.size > 6) throw IllegalStateException("bad request")
        val svc = req[0]
        if (svc != 0x22 && svc != 0x19 && svc != 0x3E) throw IllegalStateException("read only")
        val sb = StringBuilder()
        sb.append(a).append(' ').append(hex(req.size))
        for (b in req) sb.append(' ').append(hex(b))
        for (i in 0 until (6 - req.size)) sb.append(" 00")
        val r = CarLink.send(sb.toString(), 2500)
        val fr = frames(r).filter { fromAddr(it, a) }
        incomplete = false
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
            val more = fr.drop(1).toMutableList()
            val fc = CarLink.send(a + " 30 00 0A 00 00 00 00", 3000)
            more.addAll(frames(fc).filter { fromAddr(it, a) })
            for (f in more) {
                val p = f[2].toInt(16)
                if ((p and 0xF0) == 0x20) {
                    for (i in 3 until f.size) res.add(f[i].toInt(16))
                }
            }
            if (res.size < total) incomplete = true
            while (res.size > total) res.removeAt(res.size - 1)
        }
        return res
    }

    private fun describe(p: List<Int>?): String {
        if (p == null) return t("pa përgjigje", "no reply")
        if (p.isNotEmpty() && p[0] == 0x7F) {
            return t("refuzoi ", "refused ") + p.joinToString(" ") { hex(it) }
        }
        val h = p.joinToString(" ") { hex(it) }
        val ascii = p.drop(3).map { v -> if (v in 32..126) v.toChar() else '.' }.joinToString("")
        return h + "\n    ASCII: " + ascii
    }

    private fun findModules() {
        if (!CarLink.isConnected) {
            log("DEMO: 0x10, 0x12, 0x1C ...")
            return
        }
        if (busy) return
        busy = true
        log(t("Po kërkoj modulet me 22 F190, rreth 1 minutë ...", "Searching modules with 22 F190, about 1 minute ..."))
        Thread {
            try {
                prepare("20")
                log(t("Tensioni: ", "Voltage: ") + voltInfo())
                var n = 0
                for (a in 0..255) {
                    val ad = hex(a)
                    val r = CarLink.send(ad + " 03 22 F1 90 00 00 00", 700)
                    val fr = frames(r).filter { fromAddr(it, ad) }
                    if (fr.isNotEmpty()) {
                        n++
                        val f = fr[0]
                        val pci = f[2].toInt(16)
                        val hi = pci and 0xF0
                        val what = if (hi == 0x10) {
                            "VIN OK"
                        } else if (hi == 0x00 && f.size > 5 && f[3] == "7F") {
                            t("refuzoi 22: ", "refused 22: ") + f[5]
                        } else {
                            f.joinToString(" ")
                        }
                        log("0x" + ad + "  " + what)
                    }
                }
                restore()
                log(t("Mbaroi. Module që u përgjigjën: ", "Done. Modules that answered: ") + n)
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun readIdent() {
        val a = addr() ?: return
        if (!CarLink.isConnected) {
            log("DEMO 0x" + a + ": F190 = 62 F1 90 57 42 41 ... (VIN)")
            return
        }
        if (busy) return
        busy = true
        log("--- 0x" + a + " ---")
        Thread {
            try {
                prepare("40")
                log(t("Tensioni: ", "Voltage: ") + voltInfo())
                val list = listOf(
                    "F190" to "VIN",
                    "F187" to t("Numri i pjesës", "Part number"),
                    "F189" to t("Versioni i softuerit", "Software version"),
                    "F18A" to t("Furnizuesi", "Supplier"),
                    "F18C" to t("Numri serial", "Serial number"),
                    "F191" to t("Numri i hardware", "Hardware number"),
                    "F193" to t("Versioni i hardware", "Hardware version"),
                    "F194" to t("Softueri i furnizuesit", "Supplier software"),
                    "F150" to "SVK"
                )
                for ((d, label) in list) {
                    val p = readSvc(a, listOf(0x22, d.substring(0, 2).toInt(16), d.substring(2, 4).toInt(16)))
                    log(d + " " + label + ": " + describe(p))
                }
                restore()
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun readOne() {
        val a = addr() ?: return
        val d = did() ?: return
        if (!CarLink.isConnected) {
            log("DEMO 0x" + a + " DID " + d + ": 62 " + d.substring(0, 2) + " " + d.substring(2, 4) + " 00 01")
            return
        }
        if (busy) return
        busy = true
        Thread {
            try {
                prepare("40")
                val p = readSvc(a, listOf(0x22, d.substring(0, 2).toInt(16), d.substring(2, 4).toInt(16)))
                val tail = if (incomplete) t(" (jo i plotë!)", " (incomplete!)") else ""
                log("0x" + a + " DID " + d + ": " + describe(p) + tail)
                restore()
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun scanDids() {
        val a = addr() ?: return
        val d0 = did() ?: return
        val start = d0.toInt(16)
        if (!CarLink.isConnected) {
            log("DEMO: DID 3000 OK, length 120")
            return
        }
        if (busy) return
        busy = true
        log("--- " + t("Skano DID nga ", "Scan DIDs from ") + d0 + " @0x" + a + " ---")
        Thread {
            try {
                prepare("20")
                log(t("Tensioni: ", "Voltage: ") + voltInfo())
                var found = 0
                for (i in 0..255) {
                    val d = start + i
                    if (d > 0xFFFF) break
                    val dd = String.format("%04X", d)
                    val r = CarLink.send(a + " 03 22 " + dd.substring(0, 2) + " " + dd.substring(2, 4) + " 00 00 00", 700)
                    val fr = frames(r).filter { fromAddr(it, a) }
                    if (fr.isEmpty()) continue
                    val f = fr[0]
                    val pci = f[2].toInt(16)
                    val hi = pci and 0xF0
                    if (hi == 0x10 && f.size >= 4) {
                        val total = ((pci and 0x0F) shl 8) or f[3].toInt(16)
                        found++
                        log("DID " + dd + ": OK, " + t("gjatësia ", "length ") + total)
                    } else if (hi == 0x00 && f.size >= 6 && f[3] == "7F") {
                        if (f[5] != "31") {
                            found++
                            log("DID " + dd + ": " + t("refuzoi ", "refused ") + f[5])
                        }
                    } else if (hi == 0x00 && f.size >= 4 && f[3] == "62") {
                        found++
                        log("DID " + dd + ": OK, " + t("gjatësia ", "length ") + (pci and 0x0F))
                    }
                }
                restore()
                log(t("Mbaroi. DID të gjetura: ", "Done. DIDs found: ") + found)
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun prefs() = getSharedPreferences("bmwcoder_snap", Context.MODE_PRIVATE)

    private fun snap(which: String) {
        val a = addr() ?: return
        val d = did() ?: return
        if (!CarLink.isConnected) {
            log("DEMO: snapshot " + which)
            return
        }
        if (busy) return
        busy = true
        Thread {
            try {
                prepare("40")
                log(t("Tensioni: ", "Voltage: ") + voltInfo())
                val p = readSvc(a, listOf(0x22, d.substring(0, 2).toInt(16), d.substring(2, 4).toInt(16)))
                restore()
                if (p == null || p.size < 4 || p[0] != 0x62) {
                    log(t("Foto ", "Snapshot ") + which + t(" dështoi: ", " failed: ") + describe(p))
                } else {
                    val data = p.drop(3).joinToString(" ") { hex(it) }
                    prefs().edit().putString(which + "_" + a + "_" + d, data).apply()
                    BackupStore.save(this, "SNAP-" + which + " 0x" + a + " DID " + d, data)
                    val tail = if (incomplete) t(" (jo i plotë!)", " (incomplete!)") else ""
                    log(t("Foto ", "Snapshot ") + which + t(" u ruajt: ", " saved: ") +
                        (p.size - 3) + t(" bajt", " bytes") + tail)
                }
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun bin(h: String): String {
        return try {
            String.format("%8s", Integer.toBinaryString(h.toInt(16))).replace(' ', '0')
        } catch (e: Exception) {
            "-"
        }
    }

    private fun compare() {
        val a = addr() ?: return
        val d = did() ?: return
        val x = prefs().getString("A_" + a + "_" + d, null)
        val y = prefs().getString("B_" + a + "_" + d, null)
        if (x == null || y == null) {
            Toast.makeText(this, t("Duhen të dyja fotot A dhe B.", "Both snapshots A and B are needed."),
                Toast.LENGTH_LONG).show()
            return
        }
        val aa = x.split(" ").filter { it.isNotEmpty() }
        val bb = y.split(" ").filter { it.isNotEmpty() }
        val n = Math.max(aa.size, bb.size)
        log("--- " + t("Krahasim A/B", "Compare A/B") + " 0x" + a + " DID " + d + " ---")
        log(t("Gjatësia A: ", "Length A: ") + aa.size + "  B: " + bb.size)
        var diffs = 0
        for (i in 0 until n) {
            val p = if (i < aa.size) aa[i] else "--"
            val q = if (i < bb.size) bb[i] else "--"
            if (p != q) {
                diffs++
                if (diffs <= 60) {
                    log(t("bajt ", "byte ") + i + ": " + p + " → " + q + "   " + bin(p) + " → " + bin(q))
                }
            }
        }
        log(t("Ndryshime: ", "Differences: ") + diffs)
    }

    private fun saveBackup() {
        val a = addr() ?: return
        if (logBuf.length < 10) {
            Toast.makeText(this, t("Nuk ka çfarë të ruhet.", "Nothing to save."), Toast.LENGTH_SHORT).show()
            return
        }
        BackupStore.save(this, "READ 0x" + a, logBuf.toString().replace("\n", " | "))
        Toast.makeText(this, t("U ruajt te Backup.", "Saved to Backup."), Toast.LENGTH_LONG).show()
    }
}
