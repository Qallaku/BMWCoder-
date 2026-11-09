package com.bmwcoder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CodingActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var log: TextView
    private lateinit var bar: ProgressBar
    private lateinit var list: LinearLayout
    private lateinit var addrBox: EditText
    @Volatile private var stop = false
    @Volatile private var busy = false
    private val report = StringBuilder()

    override fun attachBaseContext(b: Context) = super.attachBaseContext(LocaleHelper.wrap(b))

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { f() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val sv = ScrollView(this); sv.addView(root); setContentView(sv)

        root.addView(TextView(this).apply { text = "Kodimi i modulit (vetëm lexim)"; textSize = 20f; setTextColor(Color.parseColor("#1C69D4")) })
        info = TextView(this).apply { textSize = 14f }; root.addView(info)
        root.addView(btn("1) Gjej modulet") { findModules() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(list)
        addrBox = EditText(this).apply { hint = "Adresa HEX e modulit (p.sh. 40)"; setText("40") }
        root.addView(addrBox)
        root.addView(btn("2) Lexo kodimin e modulit") { readCoding() })
        root.addView(btn("3) Diagnostikë (përgjigje të papërpunuara)") { diag() })
        root.addView(btn("Ndalo") { stop = true })
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(bar)
        log = TextView(this).apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
        root.addView(log)
        root.addView(btn("Kopjo raportin") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("r", report.toString()))
            Toast.makeText(this, "U kopjua", Toast.LENGTH_SHORT).show()
        })
        refreshInfo()
    }

    private fun refreshInfo() {
        if (!CarLink.isConnected) { info.text = "Nuk je i lidhur. Hap 'Lidhja me makinën' në menu."; return }
        Thread { val s = LowVolt.status(); runOnUiThread { info.text = s } }.start()
    }

    private fun say(t: String) { report.append(t).append('\n'); runOnUiThread { log.append(t + "\n") } }

    private fun lineBytes(line: String): List<Int>? {
        val tk = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tk.size < 3) return null
        if (tk[0].length != 3 || tk.any { !Regex("^[0-9A-Fa-f]+$").matches(it) }) return null
        return tk.drop(2).map { it.toInt(16) }
    }

    private fun frames(raw: String) = raw.split('\r', '\n').mapNotNull { lineBytes(it) }

    private fun readDid(addr: Int, did: Int, tries: Int = 2): Pair<ByteArray?, Int?> {
        val a = "%02X".format(addr)
        val req = "$a 03 22 %02X %02X 00 00 00".format(did shr 8, did and 0xFF)
        repeat(tries) {
            val raw = CarLink.send(req, 1500)
            val fr = frames(raw)
            if (fr.isNotEmpty()) {
                val f = fr[0]
                val type = f[0] shr 4
                val data: List<Int>
                if (type == 0) {
                    data = f.drop(1).take(f[0] and 0xF)
                } else if (type == 1) {
                    val total = ((f[0] and 0xF) shl 8) or f[1]
                    val buf = ArrayList<Int>(f.drop(2))
                    val more = CarLink.send("$a 30 00 0A 00 00 00 00", 3000)
                    for (c in frames(more)) if ((c[0] shr 4) == 2) buf.addAll(c.drop(1))
                    data = buf.take(total)
                } else return Pair(null, null)
                if (data.size >= 3 && data[0] == 0x7F && data[1] == 0x22) {
                    if (data[2] == 0x78) { Thread.sleep(700); return@repeat }
                    return Pair(null, data[2])
                }
                if (data.size >= 3 && data[0] == 0x62 && data[1] == (did shr 8) && data[2] == (did and 0xFF))
                    return Pair(data.drop(3).map { it.toByte() }.toByteArray(), null)
            }
            Thread.sleep(250)
        }
        return Pair(null, null)
    }

    private fun setup() {
        for (c in listOf("ATE0", "ATL0", "ATH1", "ATCAF0", "ATSP6", "ATST 80", "ATSH 6F1", "ATCM 700", "ATCF 600"))
            CarLink.send(c, 1500)
    }

    private fun restore() { for (c in listOf("ATD", "ATE0", "ATL0", "ATH0", "ATSP6")) CarLink.send(c, 1500) }

    private fun findModules() {
        if (busy) return
        if (!CarLink.isConnected) { Toast.makeText(this, "Lidhu me makinën së pari", Toast.LENGTH_LONG).show(); return }
        busy = true; stop = false; list.removeAllViews(); log.text = ""; report.setLength(0)
        Thread {
            try {
                setup()
                var n = 0
                for (a in 0..0xFF) {
                    if (stop) break
                    runOnUiThread { bar.progress = a * 100 / 255 }
                    val r = CarLink.send("%02X 02 3E 00 00 00 00 00".format(a), 500).uppercase()
                    if (r.contains("7E 00") || r.contains(" 7F ")) {
                        n++
                        val nm = EcuNames.name(this, a)
                        say("%02X  %s".format(a, nm))
                        runOnUiThread {
                            list.addView(btn("%02X · %s".format(a, nm)) { addrBox.setText("%02X".format(a)) })
                        }
                    }
                }
                say("U gjetën $n module. Zgjidh një dhe shtyp 'Lexo kodimin'.")
            } catch (e: Exception) { say("Gabim: ${e.message}") }
            finally { try { restore() } catch (_: Exception) {}; busy = false; runOnUiThread { bar.progress = 0 } }
        }.start()
    }

    private fun raw(r: String) = r.replace('\r', '|').replace('\n', '|')

    private fun diag() {
        if (busy) return
        if (!CarLink.isConnected) { Toast.makeText(this, "Lidhu me makinën së pari", Toast.LENGTH_LONG).show(); return }
        val addr = addrBox.text.toString().trim().toIntOrNull(16)
        if (addr == null || addr !in 0..255) { Toast.makeText(this, "Adresë e pavlefshme", Toast.LENGTH_SHORT).show(); return }
        busy = true; log.text = ""; report.setLength(0)
        val a = "%02X".format(addr)
        Thread {
            try {
                setup()
                say("DIAG moduli $a · " + raw(CarLink.send("ATRV", 1500)))
                val tests = listOf(
                    "ping 3E" to "$a 02 3E 00 00 00 00 00",
                    "22 F190" to "$a 03 22 F1 90 00 00 00",
                    "22 F187" to "$a 03 22 F1 87 00 00 00",
                    "22 3000" to "$a 03 22 30 00 00 00 00",
                    "22 F100" to "$a 03 22 F1 00 00 00 00",
                    "10 03 seanca" to "$a 02 10 03 00 00 00 00",
                    "22 F190 pas seancës" to "$a 03 22 F1 90 00 00 00",
                    "19 02 FF" to "$a 03 19 02 FF 00 00 00",
                    "19 01 FF" to "$a 03 19 01 FF 00 00 00",
                    "22 3000 sërish" to "$a 03 22 30 00 00 00 00"
                )
                for ((n, c) in tests) {
                    val r = raw(CarLink.send(c, 2500))
                    say("$n -> $r")
                    if (r.contains(" 10 ")) {
                        say("   FC -> " + raw(CarLink.send("$a 30 00 0A 00 00 00 00", 3000)))
                    }
                }
                say("ATDP -> " + raw(CarLink.send("ATDP", 1500)))
            } catch (e: Exception) { say("Gabim: ${e.message}") }
            finally { try { restore() } catch (_: Exception) {}; busy = false }
        }.start()
    }

    private fun readCoding() {
        if (busy) return
        if (!CarLink.isConnected) { Toast.makeText(this, "Lidhu me makinën së pari", Toast.LENGTH_LONG).show(); return }
        val addr = addrBox.text.toString().trim().toIntOrNull(16)
        if (addr == null || addr !in 0..255) { Toast.makeText(this, "Adresë e pavlefshme", Toast.LENGTH_SHORT).show(); return }
        busy = true; stop = false; log.text = ""; report.setLength(0)
        val name = EcuNames.name(this, addr)
        Thread {
            val out = StringBuilder()
            try {
                setup()
                say("Moduli %02X · %s".format(addr, name))
                say(LowVolt.status())
                val dids = ArrayList<Int>()
                dids.addAll(listOf(0xF190, 0xF187, 0xF189, 0xF18A, 0xF18C, 0xF191, 0xF193, 0xF194, 0xF150))
                dids.addAll(0x3000..0x30FF)
                var found = 0; var silent = 0
                for ((i, d) in dids.withIndex()) {
                    if (stop) { say("Ndalur."); break }
                    runOnUiThread { bar.progress = i * 100 / dids.size }
                    val (data, nrc) = readDid(addr, d)
                    if (data != null) {
                        found++; silent = 0
                        val hex = data.joinToString("") { "%02X".format(it) }
                        out.append("%04X:%s;".format(d, hex))
                        say("%04X = %s".format(d, if (hex.length > 40) hex.take(40) + "…(${data.size}B)" else hex))
                    } else if (nrc == null) {
                        silent++
                        if (silent >= 12) { say("Moduli nuk përgjigjet (tensioni i ulët?). Ndalova."); break }
                    } else silent = 0
                }
                say("Përfundoi: $found blloqe të lexuara.")
                if (found > 0) save(addr, name, out.toString())
            } catch (e: Exception) { say("Gabim: ${e.message}") }
            finally { try { restore() } catch (_: Exception) {}; busy = false; runOnUiThread { bar.progress = 0 } }
        }.start()
    }

    private fun save(addr: Int, name: String, data: String) {
        try {
            val dir = File(filesDir, "backups"); dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val mod = "ECU%02X_%s".format(addr, name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_'))
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date())
            File(dir, "${ts}_$mod.txt").writeText("module=$mod\ndate=$date\ndata=$data\n")
            say("U ruajt kopja në Backup: $mod")
        } catch (e: Exception) { say("Ruajtja dështoi: ${e.message}") }
    }
}
