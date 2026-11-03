package com.bmwcoder

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LiveActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    private lateinit var out: TextView
    @Volatile private var running = false
    @Volatile private var stopFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val head = TextView(this)
        head.text = t("Të dhëna live", "Live data")
        head.textSize = 26f
        root.addView(head)

        val start = Button(this)
        start.text = t("Fillo", "Start")
        start.setOnClickListener { startLive() }
        root.addView(start)

        val stop = Button(this)
        stop.text = t("Ndalo", "Stop")
        stop.setOnClickListener { stopFlag = true }
        root.addView(stop)

        out = TextView(this)
        out.textSize = 22f
        out.setPadding(0, 32, 0, 0)
        val sc = ScrollView(this)
        sc.addView(out)
        root.addView(sc, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        out.text = t("Lidhu te Lidhja me makinën, pastaj shtyp Fillo. Pa lidhje shfaqet demo.",
            "Connect first, then tap Start. Without a connection a demo is shown.")
    }

    override fun onPause() {
        super.onPause()
        stopFlag = true
    }

    private fun ask(pid: String, need: Int): List<Int>? {
        val r = CarLink.send("01" + pid, 1500)
        val h = r.replace(" ", "").replace("\r", "").replace("\n", "")
        val key = "41" + pid
        val i = h.indexOf(key)
        if (i < 0) return null
        val rest = h.substring(i + 4)
        if (rest.length < need * 2) return null
        val res = ArrayList<Int>()
        for (k in 0 until need) {
            val s = rest.substring(k * 2, k * 2 + 2)
            if (!s.all { ch -> ch.isDigit() || ch in 'A'..'F' }) return null
            res.add(s.toInt(16))
        }
        return res
    }

    private fun show(s: String) {
        runOnUiThread { out.text = s }
    }

    private fun startLive() {
        if (!CarLink.isConnected) {
            show("DEMO\n\nRPM: 850\n" + t("Shpejtësia", "Speed") + ": 0 km/h\n" +
                t("Ftohësi", "Coolant") + ": 88 °C\n" + t("Ngarkesa", "Load") + ": 21 %\n" +
                t("Tensioni", "Voltage") + ": 13.8 V")
            return
        }
        if (running) return
        running = true
        stopFlag = false
        Thread {
            try {
                CarLink.send("ATD", 2000)
                CarLink.send("ATE0", 2000)
                CarLink.send("ATL0", 2000)
                CarLink.send("ATH0", 2000)
                CarLink.send("ATSP6", 3000)
                CarLink.send("ATST 40", 2000)
                while (!stopFlag) {
                    val rpm = ask("0C", 2)
                    val spd = ask("0D", 1)
                    val clt = ask("05", 1)
                    val ld = ask("04", 1)
                    val thr = ask("11", 1)
                    val iat = ask("0F", 1)
                    val v = CarLink.send("ATRV", 1500).trim()
                    val sb = StringBuilder()
                    sb.append("RPM: ").append(if (rpm == null) "-" else ((rpm[0] * 256 + rpm[1]) / 4).toString()).append("\n")
                    sb.append(t("Shpejtësia", "Speed")).append(": ")
                        .append(if (spd == null) "-" else spd[0].toString() + " km/h").append("\n")
                    sb.append(t("Ftohësi", "Coolant")).append(": ")
                        .append(if (clt == null) "-" else (clt[0] - 40).toString() + " °C").append("\n")
                    sb.append(t("Ngarkesa", "Load")).append(": ")
                        .append(if (ld == null) "-" else (ld[0] * 100 / 255).toString() + " %").append("\n")
                    sb.append(t("Gazi", "Throttle")).append(": ")
                        .append(if (thr == null) "-" else (thr[0] * 100 / 255).toString() + " %").append("\n")
                    sb.append(t("Ajri i hyrjes", "Intake air")).append(": ")
                        .append(if (iat == null) "-" else (iat[0] - 40).toString() + " °C").append("\n")
                    sb.append(t("Tensioni", "Voltage")).append(": ").append(v)
                    show(sb.toString())
                    Thread.sleep(300)
                }
                show(out.text.toString() + "\n\n" + t("U ndal.", "Stopped."))
            } catch (e: Exception) {
                show(t("Gabim: ", "Error: ") + e.message)
            } finally {
                running = false
            }
        }.start()
    }
}
