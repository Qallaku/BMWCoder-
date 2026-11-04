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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val head = TextView(this)
        head.text = t("Lexim i modulit (vetëm lexim)", "Module read (read only)")
        head.textSize = 24f
        root.addView(head)

        addrBox = EditText(this)
        addrBox.hint = t("Adresa e modulit, hex (p.sh. 1C)", "Module address, hex (e.g. 1C)")
        root.addView(addrBox)

        didBox = EditText(this)
        didBox.hint = t("DID, hex 4 shifra (p.sh. F190)", "DID, 4 hex digits (e.g. F190)")
        root.addView(didBox)

        fun add(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { action() }
            root.addView(b)
        }

        add(t("Lexo identifikimin e modulit", "Read module identification")) { readIdent() }
        add(t("Lexo këtë DID", "Read this DID")) { readOne() }
        add(t("Ruaj te Backup", "Save to Backup")) { saveBackup() }

        out = TextView(this)
        out.textSize = 14f
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
        log(t("Lidhu fillimisht. Ky ekran dërgon vetëm komanda leximi (22). Pa lidhje punon në demo.",
            "Connect first. This screen only sends read commands (22). Without a connection it runs in demo mode."))
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

    private fun restore() {
        CarLink.send("ATD", 2000)
        CarLink.send("ATE0", 2000)
        CarLink.send("ATL0", 2000)
        CarLink.send("ATH0", 2000)
        CarLink.send("ATSP6", 3000)
    }

    private fun prepare() {
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

    private fun read22(a: String, did: String): List<Int>? {
        val req = "22 " + did.substring(0, 2) + " " + did.substring(2, 4)
        if (!req.startsWith("22 ")) throw IllegalStateException("read only")
        val sb = StringBuilder()
        sb.append(a).append(" 03 ").append(req).append(" 00 00 00")
        val r = CarLink.send(sb.toString(), 1500)
        val fr = frames(r).filter { fromAddr(it, a) }
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
            var guard = 0
            while (res.size < total && guard < 40) {
                val fc = CarLink.send(a + " 30 00 00 00 00 00 00", 2000)
                for (f in frames(fc).filter { fromAddr(it, a) }) {
                    val p = f[2].toInt(16)
                    if ((p and 0xF0) == 0x20) {
                        for (i in 3 until f.size) res.add(f[i].toInt(16))
                    }
                }
                guard++
                if (guard >= 1) break
            }
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
                prepare()
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
                for ((did, label) in list) {
                    val p = read22(a, did)
                    log(did + " " + label + ": " + describe(p))
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
        val d = didBox.text.toString().trim().uppercase()
        if (d.length != 4 || !isHex(d)) {
            Toast.makeText(this, t("DID duhet 4 shifra hex.", "DID must be 4 hex digits."),
                Toast.LENGTH_LONG).show()
            return
        }
        if (!CarLink.isConnected) {
            log("DEMO 0x" + a + " DID " + d + ": 62 " + d.substring(0, 2) + " " + d.substring(2, 4) + " 00 01")
            return
        }
        if (busy) return
        busy = true
        Thread {
            try {
                prepare()
                val p = read22(a, d)
                log("0x" + a + " DID " + d + ": " + describe(p))
                restore()
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                busy = false
            }
        }.start()
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
