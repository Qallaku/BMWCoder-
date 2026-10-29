package com.bmwcoder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.InputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    private lateinit var info: TextView
    private lateinit var scroll: ScrollView
    private lateinit var list: ListView
    private var btAdapter: BluetoothAdapter? = null
    private var devices: List<BluetoothDevice> = emptyList()
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val logBuf = StringBuilder()
    @Volatile private var busy = false

    private fun t(sq: String, en: String) = if (LocaleHelper.get(this) == "en") en else sq

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        val title = TextView(this)
        title.text = t("Lidhja me makinën", "Connect to car")
        title.textSize = 26f
        root.addView(title)

        val btn = Button(this)
        btn.text = t("Shfaq adapterët e çiftuar", "Show paired adapters")
        btn.setOnClickListener { showDevices() }
        root.addView(btn)

        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos < devices.size) connect(devices[pos])
        }
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        info = TextView(this)
        info.textSize = 14f
        scroll = ScrollView(this)
        scroll.addView(info)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f))

        val copy = Button(this)
        copy.text = t("Kopjo raportin", "Copy report")
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", logBuf.toString()))
            Toast.makeText(this, t("U kopjua", "Copied"), Toast.LENGTH_SHORT).show()
        }
        root.addView(copy)

        setContentView(root)
        log(t("Shtyp butonin për të parë adapterët Bluetooth.",
            "Tap the button to see Bluetooth adapters."))
    }

    private fun log(msg: String) {
        runOnUiThread {
            logBuf.append(msg).append("\n")
            info.text = logBuf.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showDevices() {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (need.isNotEmpty()) {
                requestPermissions(need.toTypedArray(), 1)
                log(t("Lejo Bluetooth, pastaj shtyp përsëri butonin.",
                    "Allow Bluetooth, then tap the button again."))
                return
            }
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        btAdapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            log(t("Bluetooth është i fikur. Ndize dhe provo përsëri.",
                "Bluetooth is off. Turn it on and try again."))
            return
        }
        devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            log(t("Asnjë pajisje e çiftuar.", "No paired devices."))
            return
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            devices.map { "${it.name}  (${it.address})" })
        log(t("Shtyp adapterin tënd (p.sh. Android-Vlink) për t'u lidhur.",
            "Tap your adapter (e.g. Android-Vlink) to connect."))
    }

    private fun openSocket(device: BluetoothDevice): BluetoothSocket? {
        val strategies = listOf<Pair<String, () -> BluetoothSocket>>(
            "1/3 SPP" to { device.createRfcommSocketToServiceRecord(sppUuid) },
            "2/3 SPP insecure" to { device.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            "3/3 channel 1" to {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            }
        )
        for (attempt in 1..2) {
            for ((name, make) in strategies) {
                var s: BluetoothSocket? = null
                try {
                    try { btAdapter?.cancelDiscovery() } catch (ignored: Exception) {}
                    s = make()
                    s.connect()
                    log(t("Lidhja OK me metodën ", "Connected with method ") + name)
                    return s
                } catch (e: Exception) {
                    log("[$attempt] $name: ${e.message}")
                    try { s?.close() } catch (ignored: Exception) {}
                    Thread.sleep(700)
                }
            }
        }
        return null
    }

    private fun clean(s: String) = s.replace("\r", " ").replace("\n", " ").trim()

    private fun connect(device: BluetoothDevice) {
        if (busy) return
        busy = true
        logBuf.setLength(0)
        log(t("Po lidhem me ${device.name} ...", "Connecting to ${device.name} ..."))
        Thread {
            var socket: BluetoothSocket? = null
            try {
                socket = openSocket(device)
                if (socket == null) {
                    log(t("Lidhja dështoi. Kontrollo: kontakti i ndezur, adapteri në portën OBD, aplikacionet e tjera OBD të mbyllura.",
                        "Connection failed. Check: ignition on, adapter in the OBD port, other OBD apps closed."))
                    return@Thread
                }
                val out = socket.outputStream
                val inp = socket.inputStream

                fun send(cmd: String, timeout: Long = 8000): String {
                    while (inp.available() > 0) inp.read()
                    out.write((cmd + "\r").toByteArray())
                    out.flush()
                    return readUntilPrompt(inp, timeout).trim()
                }

                log("ATZ: " + clean(send("ATZ", 4000)))
                send("ATE0")
                send("ATL0")
                send("ATH0")
                log(t("Tensioni: ", "Voltage: ") + clean(send("ATRV", 3000)) +
                    t("  (duhet ~12-14 V me kontaktin ndezur)", "  (should be ~12-14 V with ignition on)"))
                send("ATSP0")
                log("0100: " + clean(send("0100", 15000)))
                log(t("Po kërkoj VIN-in ...", "Requesting VIN ..."))
                val resp = send("0902", 10000)
                val vin = parseVin(resp)
                if (vin != null) {
                    log("VIN: $vin")
                } else {
                    log(t("VIN nuk u lexua. Përgjigja: ", "VIN not read. Reply: ") + clean(resp))
                }
            } catch (e: Exception) {
                log(t("Gabim: ", "Error: ") + e.message)
            } finally {
                try { socket?.close() } catch (ignored: Exception) {}
                busy = false
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
