package com.bmwcoder

import android.content.Context

object EcuNames {
    private val base = mapOf(
        0x10 to "ZGW (Gateway)", 0x12 to "DME (Motori)", 0x18 to "EGS (Transmisioni)",
        0x29 to "DSC (Frenat/Stabiliteti)", 0x40 to "FEM / CAS (Moduli i përparmë)",
        0x60 to "KOMBI (Instrument Cluster)", 0x63 to "Headunit", 0x72 to "ACSM (Airbag) - MOS PREK",
        0x78 to "Klima (IHKA)", 0x5F to "Headunit/Kamera", 0xA0 to "Moduli i pasmë (REM)"
    )

    fun name(ctx: Context, addr: Int): String {
        val p = ctx.getSharedPreferences("bmwcoder_ecu", Context.MODE_PRIVATE)
        return p.getString("n%02X".format(addr), null) ?: base[addr] ?: "Modul i panjohur"
    }

    fun rename(ctx: Context, addr: Int, n: String) {
        ctx.getSharedPreferences("bmwcoder_ecu", Context.MODE_PRIVATE)
            .edit().putString("n%02X".format(addr), n).apply()
    }
}
