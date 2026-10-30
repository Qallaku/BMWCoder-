package com.bmwcoder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

    data class Ecu(val name: String, val descSq: String, val descEn: String)

    private val all = listOf(
        Ecu("Active Sound Design", "Zëri aktiv i motorit", "Active engine sound"),
        Ecu("Advanced Crash Safety Module", "Kujtesa e rripit të sigurimit", "Seatbelt reminder"),
        Ecu("Air Conditioning", "Klima", "Air conditioning"),
        Ecu("Allround View Camera", "Kamera anësore", "Side view camera"),
        Ecu("Electronic Transmission Control", "Transmisioni", "Transmission"),
        Ecu("Front Electronic Module", "Start-Stop, dyert/dritaret, ndriçimi, pasqyrat",
            "Auto Start Stop, doors/windows, lighting, mirrors"),
        Ecu("Headunit", "Ekrani dhe multimedia", "Display and multimedia")
    )
    private var shown: List<Ecu> = all
    private fun isEn() = LocaleHelper.get(this) == "en"
    private fun desc(e: Ecu) = if (isEn()) e.descEn else e.descSq

    private inner class EcuAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val e = shown[position]
            val box = LinearLayout(this@ModulesActivity)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(8, 24, 8, 24)
            val n = TextView(this@ModulesActivity)
            n.text = e.name
            n.textSize = 18f
            n.setTypeface(null, Typeface.BOLD)
            val d = TextView(this@ModulesActivity)
            d.text = desc(e)
            d.textSize = 14f
            box.addView(n)
            box.addView(d)
            return box
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 24)

        val title = TextView(this)
        title.text = if (isEn()) "Modules (demo)" else "Modulet (demo)"
        title.textSize = 26f
        root.addView(title)

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
