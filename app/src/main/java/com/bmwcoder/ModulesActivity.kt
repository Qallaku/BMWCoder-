package com.bmwcoder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class ModulesActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    data class Ecu(val name: String, val icon: String, val sq: String, val en: String)

    private val all = listOf(
        Ecu("Active Sound Design", "🔊", "Zëri i motorit nga altoparlantët", "Engine sound through the speakers"),
        Ecu("Advanced Crash Safety Module", "🛡", "Airbag-ët, rripat dhe kujtesa e rripit", "Airbags, seatbelts and seatbelt reminder"),
        Ecu("Air Conditioning", "❄", "Klima", "Climate control"),
        Ecu("Allround View Camera", "📷", "Kamerat rrethuese", "Surround-view cameras"),
        Ecu("Electronic Transmission Control", "⚙", "Transmisioni automatik", "Automatic transmission"),
        Ecu("Front Electronic Module", "💡", "Start-Stop, dyert, dritaret, ndriçimi, pasqyrat",
            "Auto Start-Stop, doors, windows, lighting, mirrors"),
        Ecu("Headunit", "📺", "Ekrani iDrive, audio, video", "iDrive display, audio, video"),
        Ecu("Instrument Cluster", "🎛", "Paneli i instrumenteve, mënyra e vozitjes, paralajmërimet",
            "Instrument cluster, driving mode, warnings"),
        Ecu("Integrated Chassis Management", "🛞", "Shasia, cruise control aktiv, mënyra e vozitjes",
            "Chassis, active cruise control, driving mode"),
        Ecu("Power Tailgate Module", "🚪", "Bagazhi me hapje elektrike", "Power tailgate"),
        Ecu("Rear Electronic Module", "🅿", "PDC, ndriçimi i pasmë, kamera e pasme", "PDC, rear lighting, rear camera"),
        Ecu("Roof Function Center", "☂", "Drita e brendshme, çatia, konfirmimi akustik, alarmi",
            "Interior light, roof, acoustic confirmation, alarm"),
        Ecu("Seat Module Driver", "💺", "Ngrohja dhe rregullimi i ndenjëses së shoferit",
            "Driver seat heating and adjustment")
    )
    private var shown: List<Ecu> = all
    private fun isEn() = LocaleHelper.get(this) == "en"
    private fun desc(e: Ecu) = if (isEn()) e.en else e.sq
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class EcuAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val e = shown[position]
            val row = LinearLayout(this@ModulesActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(14), dp(8), dp(14))

            val ic = TextView(this@ModulesActivity)
            ic.text = e.icon
            ic.textSize = 26f
            ic.setPadding(0, 0, dp(16), 0)
            row.addView(ic)

            val col = LinearLayout(this@ModulesActivity)
            col.orientation = LinearLayout.VERTICAL
            val n = TextView(this@ModulesActivity)
            n.text = e.name
            n.textSize = 17f
            n.setTypeface(null, Typeface.BOLD)
            val d = TextView(this@ModulesActivity)
            d.text = desc(e)
            d.textSize = 14f
            col.addView(n)
            col.addView(d)
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 24)

        val head = TextView(this)
        head.text = if (isEn()) "Modules (ECU)" else "Modulet (ECU)"
        head.textSize = 26f
        root.addView(head)

        val search = EditText(this)
        search.hint = if (isEn()) "Search..." else "Kërko..."
        root.addView(search)

        val list = ListView(this)
        val adapter = EcuAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = Intent(this, ModuleDetailActivity::class.java)
            i.putExtra("name", shown[pos].name)
            startActivity(i)
        }
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown = if (q.isEmpty()) all else all.filter {
                    it.name.lowercase().contains(q) || desc(it).lowercase().contains(q)
                }
                adapter.notifyDataSetChanged()
            }
        })
        setContentView(root)
    }
}
