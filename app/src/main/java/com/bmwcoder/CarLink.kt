package com.bmwcoder

import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream

object CarLink {
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var inp: InputStream? = null
    var deviceName: String = ""

    val isConnected: Boolean
        get() = socket?.isConnected == true

    fun attach(s: BluetoothSocket, name: String) {
        close()
        socket = s
        out = s.outputStream
        inp = s.inputStream
        deviceName = name
    }

    @Synchronized
    fun send(cmd: String, timeoutMs: Long = 5000): String {
        val o = out ?: throw IllegalStateException("not connected")
        val i = inp ?: throw IllegalStateException("not connected")
        while (i.available() > 0) i.read()
        o.write((cmd + "\r").toByteArray())
        o.flush()
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (i.available() > 0) {
                val c = i.read()
                if (c < 0) break
                if (c.toChar() == '>') break
                sb.append(c.toChar())
            } else {
                Thread.sleep(10)
            }
        }
        return sb.toString().trim()
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
        }
        socket = null
        out = null
        inp = null
    }
}
