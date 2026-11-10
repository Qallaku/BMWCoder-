package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class FunctionsActivity : Activity() {

    override fun attachBaseContext(b: Context) = super.attachBaseContext(LocaleHelper.wrap(b))

    data class Fn(val addr: Int, val did: Int, val idx: Int, val mask: Int,
                  val name: String, val la: String, val va: Int, val lb: String, val vb: Int)

    private var addr = 0x40
    private lateinit var root: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun clean(s: String) = s.replace("\t", " ").replace("\n", " ").trim()

    // ---------- ruajtja e funksioneve të mësuara ----------
    private fun prefs() = getSharedPreferences("bmwcoder_funcs", Context.MODE_PRIVATE)

    private fun loadAll(): List<Fn> {
        val raw = prefs().getString("list", "") ?: ""
        val out = ArrayList<Fn>()
        for (ln in raw.split("\n")) {
            val p = ln.split("\t")
            if (p.size < 9) continue
            try {
                out.add(Fn(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(),
                    p[4], p[5], p[6].toInt(), p[7], p[8].toInt()))
            } catch (e: Exception) { }
        }
        return out
    }

    private fun saveAll(l: List<Fn>) {
        val s = l.joinToString("\n") {
            listOf(it.addr, it.did, it.idx, it.mask, it.name, it.la, it.va, it.lb, it.vb).joinToString("\t")
        }
        prefs().edit().putString("list", s).apply()
    }

    // ---------- leximet e ruajtura ----------
    private fun parse(d: String): Map<Int, String> {
        val m = LinkedHashMap<Int, String>()
        for (p in d.split(';')) {
            val i = p.indexOf(':')
            if (i > 0) {
                val k = p.substring(0, i).toIntOrNull(16)
                if (k != null) m[k] = p.substring(i + 1)
            }
        }
        return m
    }

    private fun readings(): List<Pair<String, Map<Int, String>>> {
        val tag = "_ECU%02X_".format(addr)
        val fs = File(filesDir, "backups").listFiles()
            ?.filter { it.name.contains(tag) && it.name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: emptyList()
        return fs.map { f ->
            val ln = f.readLines().firstOrNull { it.startsWith("data=") } ?: "data="
            Pair(f.name, parse(ln.substring(5)))
        }
    }

    private fun byteAt(m: Map<Int, String>, did: Int, idx: Int): Int? {
        val h = m[did] ?: return null
        if (idx * 2 + 2 > h.length) return null
        return h.substring(idx * 2, idx * 2 + 2).toInt(16)
    }

    // ---------- ekrani ----------
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        addr = intent.getIntExtra("addr", 0x40)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)
        rebuild()
    }

    private fun rebuild() {
        root.removeAllViews()
        val title = TextView(this)
        title.text = "Funksionet · %02X · %s".format(addr, EcuNames.name(this, addr))
        title.textSize = 20f
        title.setTextColor(Color.parseColor("#1C69D4"))
        root.addView(title)

        val rd = readings()
        val note = TextView(this)
        note.textSize = 13f
        note.text = "${rd.size} lexime të ruajtura për këtë modul. Shkrimi është i çaktivizuar derisa funksioni të verifikohet."
        root.addView(note)

        val learn = Button(this)
        learn.text = "Mëso funksion të ri (krahaso dy leximet e fundit)"
        learn.isAllCaps = false
        learn.setOnClickListener { startLearn(rd) }
        root.addView(learn)

        val read = Button(this)
        read.text = "Lexo modulin nga makina"
        read.isAllCaps = false
        read.setOnClickListener { startActivity(Intent(this, CodingActivity::class.java)) }
        root.addView(read)

        val mine = loadAll().filter { it.addr == addr }
        if (mine.isEmpty()) {
            val t = TextView(this)
            t.text = "\nNuk ke mësuar ende asnjë funksion. Lexo modulin, ndrysho një funksion në makinë ose BimmerCode, lexo prapë, pastaj shtyp 'Mëso funksion të ri'."
            root.addView(t)
            return
        }
        val last = if (rd.isEmpty()) null else rd.last().second
        for (f in mine) {
            val cur = if (last == null) null else byteAt(last, f.did, f.idx)
            val txt = when {
                cur == null -> "nuk ka lexim"
                (cur and f.mask) == (f.va and f.mask) -> f.la
                (cur and f.mask) == (f.vb and f.mask) -> f.lb
                else -> "vlerë tjetër: %02X".format(cur and f.mask)
            }
            val row = TextView(this)
            row.text = "${f.name}\nTani: $txt"
            row.textSize = 17f
            row.setPadding(dp(8), dp(14), dp(8), dp(14))
            row.setOnClickListener { info(f, txt) }
            root.addView(row)
        }
    }

    private fun info(f: Fn, now: String) {
        val msg = "Vendndodhja: bllok %04X, bajt %d, maskë %02X\n".format(f.did, f.idx, f.mask) +
            "${f.la} = %02X\n${f.lb} = %02X\nTani: $now\n\n".format(f.va and f.mask, f.vb and f.mask) +
            "Ndryshimi në makinë ende nuk është aktivizuar për këtë funksion."
        AlertDialog.Builder(this).setTitle(f.name).setMessage(msg)
            .setPositiveButton("Mbyll", null)
            .setNegativeButton("Fshi funksionin") { _, _ ->
                saveAll(loadAll().filter { it != f })
                rebuild()
            }.show()
    }

    // ---------- mësimi ----------
    private fun startLearn(rd: List<Pair<String, Map<Int, String>>>) {
        if (rd.size < 2) {
            AlertDialog.Builder(this).setMessage("Duhen të paktën 2 lexime. Lexo modulin, ndrysho një funksion, pastaj lexo prapë.")
                .setPositiveButton("OK", null).show()
            return
        }
        val a = rd[rd.size - 2].second
        val b = rd[rd.size - 1].second
        val diffs = ArrayList<Triple<Int, Int, Pair<Int, Int>>>()
        for ((did, ha) in a) {
            if (did < 0x3000) continue
            val hb = b[did] ?: continue
            val n = minOf(ha.length, hb.length) / 2
            for (i in 0 until n) {
                val x = ha.substring(i * 2, i * 2 + 2).toInt(16)
                val y = hb.substring(i * 2, i * 2 + 2).toInt(16)
                if (x != y) diffs.add(Triple(did, i, Pair(x, y)))
            }
        }
        if (diffs.isEmpty()) {
            AlertDialog.Builder(this).setMessage("Dy leximet e fundit janë të njëjta. Ndrysho një funksion dhe lexo prapë.")
                .setPositiveButton("OK", null).show()
            return
        }
        if (diffs.size > 12) {
            AlertDialog.Builder(this).setMessage("U ndryshuan ${diffs.size} bajte, shumë për një funksion të vetëm. Ndrysho vetëm NJË funksion dhe lexo prapë.")
                .setPositiveButton("OK", null).show()
            return
        }
        val items = diffs.map { "%04X bajt %d: %02X → %02X".format(it.first, it.second, it.third.first, it.third.second) }
        AlertDialog.Builder(this).setTitle("Cili bajt është funksioni yt?")
            .setItems(items.toTypedArray()) { _, w -> nameIt(diffs[w]) }
            .setNegativeButton("Anulo", null).show()
    }

    private fun nameIt(d: Triple<Int, Int, Pair<Int, Int>>) {
        val x = d.third.first
        val y = d.third.second
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(16), dp(8), dp(16), dp(8))
        val n = EditText(this); n.hint = "Emri në shqip (p.sh. Drita e Angel Eyes)"
        val la = EditText(this); la.hint = "Çfarë do të thotë %02X (para ndryshimit)? p.sh. Joaktiv".format(x)
        val lb = EditText(this); lb.hint = "Çfarë do të thotë %02X (pas ndryshimit)? p.sh. Aktiv".format(y)
        n.inputType = InputType.TYPE_CLASS_TEXT
        ll.addView(n); ll.addView(la); ll.addView(lb)
        AlertDialog.Builder(this).setTitle("%04X bajt %d".format(d.first, d.second)).setView(ll)
            .setPositiveButton("Ruaj") { _, _ ->
                val nm = clean(n.text.toString())
                if (nm.isEmpty()) return@setPositiveButton
                val bits = Integer.bitCount(x xor y)
                val mask = if (bits <= 1) (x xor y) else 0xFF
                val all = loadAll().toMutableList()
                all.add(Fn(addr, d.first, d.second, mask, nm,
                    clean(la.text.toString()).ifEmpty { "vlera A" }, x,
                    clean(lb.text.toString()).ifEmpty { "vlera B" }, y))
                saveAll(all)
                rebuild()
            }
            .setNegativeButton("Anulo", null).show()
    }
}
