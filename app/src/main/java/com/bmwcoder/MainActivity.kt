package com.bmwcoder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val title = TextView(this).apply {
            text = "BmwCoder"
            textSize = 28f
        }
        val btn = Button(this).apply {
            text = "Shfaq adapterët e çiftuar"
            setOnClickListener { listDevices() }
        }
        info = TextView(this).apply {
            textSize = 16f
            text = "Shtyp butonin për të parë adapterët Bluetooth."
        }
        root.addView(title)
        root.addView(btn)
        root.addView(info)
        setContentView(root)
    }

    @SuppressLint("MissingPermission")
    private fun listDevices() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            info.text = "Lejo Bluetooth, pastaj shtyp përsëri butonin."
            return
        }
        val adapter: BluetoothAdapter? =
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            info.text = "Bluetooth është i fikur. Ndize dhe provo përsëri."
            return
        }
        val list = adapter.bondedDevices.joinToString("\n") { "• ${it.name}  (${it.address})" }
        info.text = if (list.isEmpty()) "Asnjë pajisje e çiftuar." else list
    }
}
