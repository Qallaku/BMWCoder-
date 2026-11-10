package com.bmwcoder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class BlocksActivity : Activity() {

    override fun attachBaseContext(b: Context) = super.attachBaseContext(LocaleHelper.wrap(b))

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Emrat e blloqeve të FEM_01 (siç shfaqen te BimmerCode Expert mode)
    private val fem = mapOf(
        0x3000 to "EcuHwConfiguration", 0x3022 to "TcIntegration", 0x3023 to "TcMaster2",
        0x3030 to "CaMaster", 0x3031 to "CaIntegration", 0x3032 to "CaIntegrationKeyLocation",
        0x3040 to "CIMaster", 0x3041 to "CIIntegration", 0x3050 to "PwClient",
        0x3051 to "PwDriverBlock0", 0x3054 to "PwApplIntegration", 0x3055 to "PwDriver",
        0x3060 to "LceMaster", 0x3061 to "LceLciOutputChannels", 0x3062 to "LceLampMapping1",
        0x3063 to "LceLampMapping2", 0x3064 to "LceConfiguration", 0x3066 to "LceTmsCoding1",
        0x3067 to "LceTmsCoding2", 0x3070 to "LciMaster"
    )

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

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val addr = intent.getIntExtra("addr", 0x40)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)

        val title = TextView(this)
        title.text = "Blloqet e kodimit · %02X · %s".format(addr, EcuNames.name(this, addr))
        title.textSize = 20f
        title.setTextColor(Color.parseColor("#1C69D4"))
        root.addView(title)

        val read = Button(this)
        read.text = "Lexo përsëri nga makina"
        read.isAllCaps = false
        read.setOnClickListener { startActivity(Intent(this, CodingActivity::class.java)) }
        root.addView(read)

        val fns = Button(this)
        fns.text = "Funksionet (në shqip)"
        fns.isAllCaps = false
        fns.setOnClickListener {
            val i = Intent(this, FunctionsActivity::class.java)
            i.putExtra("addr", addr)
            startActivity(i)
        }
        root.addView(fns)

        val note = TextView(this)
        note.textSize = 13f
        root.addView(note)

        val tag = "_ECU%02X_".format(addr)
        val files = File(filesDir, "backups").listFiles()
            ?.filter { it.name.contains(tag) && it.name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) {
            note.text = "Nuk ka lexim të ruajtur për këtë modul. Shtyp 'Lexo përsëri nga makina', pastaj 'Lexo kodimin'."
            return
        }
        val last = files.last()
        val date = last.readLines().firstOrNull { it.startsWith("date=") }?.substring(5) ?: ""
        val line = last.readLines().firstOrNull { it.startsWith("data=") } ?: "data="
        val blocks = parse(line.substring(5))
        note.text = "Leximi i fundit: $date · ${blocks.size} blloqe · ${files.size} kopje në Backup.\n" +
            "Shkrimi është i çaktivizuar derisa funksioni të verifikohet në makinë."

        for ((did, hex) in blocks.toSortedMap()) {
            if (did < 0x3000) continue
            val nm = if (addr == 0x40) (fem[did] ?: "") else ""
            val row = TextView(this)
            row.text = "%04X  %s\n%d bajte".format(did, nm, hex.length / 2)
            row.textSize = 16f
            row.setPadding(dp(8), dp(12), dp(8), dp(12))
            row.setOnClickListener { show(did, nm, hex) }
            root.addView(row)
        }
    }

    private fun show(did: Int, nm: String, hex: String) {
        val sb = StringBuilder()
        var i = 0
        var n = 0
        while (i + 2 <= hex.length) {
            val b = hex.substring(i, i + 2).toInt(16)
            sb.append("%3d: %02X  %s\n".format(n, b, Integer.toBinaryString(b).padStart(8, '0')))
            i += 2
            n++
        }
        val tv = TextView(this)
        tv.text = sb.toString()
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = 14f
        tv.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sc = ScrollView(this)
        sc.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("%04X %s".format(did, nm))
            .setView(sc)
            .setPositiveButton("Mbyll", null)
            .show()
    }
}
