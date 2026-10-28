package com.bmwcoder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.io.InputStream
import java.util.Locale
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var list: ListView
    private var devices: List<BluetoothDevice> = emptyList()
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun t(sq: String, en: String) =
        if (Locale.getDefault().language == "en") en else sq

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
        }
        val btn = Button(this).apply {
            text = getString(R.string.btn_devices)
            setOnClickListener { showDevices() }
        }
        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos < devices.size) connect(devices[pos])
        }
        info = TextView(this).apply {
            textSize = 15f
            text = getString(R.string.hint_start)
        }
        val scroll = ScrollView(this)
        scroll.addView(info)
        root.addView(title)
        root.addView(btn)
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showDevices() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            info.text = getString(R.string.msg_allow_bt)
            return
        }
        val adapter: BluetoothAdapter? =
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            info.text = getString(R.string.msg_bt_off)
            return
        }
        devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            info.text = getString(R.string.msg_no_devices)
            return
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            devices.map { "${it.name}  (${it.address})" })
        info.text = t("Shtyp adapterin tënd (p.sh. Android-Vlink) për t'u lidhur.",
            "Tap your adapter (e.g. Android-Vlink) to connect.")
    }

    private fun log(msg: String) {
        runOnUiThread { info.append("\n" + msg) }
    }

    private fun connect(device: BluetoothDevice) {
        info.text = t("Po lidhem me ${device.name}...", "Connecting to ${device.name}...")
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                val out = socket.outputStream
                val inp = socket.inputStream
                log(t("Lidhja me adapterin u krye.", "Adapter connected."))

                fun send(cmd: String, timeout: Long = 8000): String {
                    while (inp.available() > 0) inp.read()
                    out.write((cmd + "\r").toByteArray())
                    out.flush()
                    return readUntilPrompt(inp, timeout).trim()
                }

                log("ATZ: " + send("ATZ", 4000).replace("\r", " ").replace("\n", " "))
                send("ATE0")
                send("ATL0")
                send("ATH0")
                send("ATSP6")
                log(t("Po kërkoj VIN-in (ndiz kontaktin e makinës)...",
                    "Requesting VIN (turn the ignition on)..."))
                val resp = send("0902", 10000)
                val vin = parseVin(resp)
                if (vin != null) {
                    log("VIN: $vin")
                } else {
                    log(t("VIN nuk u lexua. Përgjigja e adapterit:", "VIN not read. Adapter reply:"))
                    log(resp.replace("\r", "\n"))
                }
                socket.close()
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            }
        }.start()
    }

    private fun readUntilPrompt(inp: InputStream, timeoutMs: Long): String {
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (inp.available() > 0) {
                val c = inp.read()
                if (c < 0) break
                if (c.toChar() == '>') break
                sb.append(c.toChar())
            } else {
                Thread.sleep(20)
            }
        }
        return sb.toString()
    }

    private fun parseVin(resp: String): String? {
        val hex = StringBuilder()
        resp.split("\r", "\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            var l = line.replace(" ", "")
            if (l.length >= 2 && l[1] == ':') l = l.substring(2)
            if (l.matches(Regex("^[0-9A-F]{3}$"))) return@forEach
            if (l.matches(Regex("^[0-9A-F]+$"))) hex.append(l)
        }
        val h = hex.toString()
        val idx = h.indexOf("490201")
        if (idx < 0) return null
        val data = h.substring(idx + 6)
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < data.length) {
            val b = data.substring(i, i + 2).toInt(16)
            if (b in 48..57 || b in 65..90) sb.append(b.toChar())
            i += 2
        }
        val s = sb.toString()
        return if (s.length >= 17) s.takeLast(17) else null
    }
}
