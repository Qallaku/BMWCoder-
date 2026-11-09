package com.bmwcoder

/**
 * Lexim i qëndrueshëm pa motor ndezur (tension i ulët).
 * VETËM LEXIM. Shkrimi mbetet i bllokuar nën 12.5 V në SafeWrite.
 */
object LowVolt {

    /** Tensioni aktual (Volt) ose null nëse s'lexohet. */
    fun volts(): Double? {
        if (!CarLink.isConnected) return null
        val r = CarLink.send("ATRV", 1500)
        val m = Regex("([0-9]+\\.?[0-9]*)").find(r) ?: return null
        return m.value.toDoubleOrNull()
    }

    /** Mesazh statusi për banerin. */
    fun status(): String {
        val v = volts() ?: return "Tensioni: nuk dihet"
        return when {
            v >= 13.0 -> "Tensioni %.1f V · OK (leximi dhe shkrimi lejohen)".format(v)
            v >= 12.5 -> "Tensioni %.1f V · OK për lexim, shkrimi i kufizuar".format(v)
            v >= 11.5 -> "Tensioni %.1f V · vetëm LEXIM. Lidh karikues 13–14 V".format(v)
            else -> "Tensioni %.1f V · shumë i ulët, modulet mund të mos përgjigjen".format(v)
        }
    }

    private fun bad(r: String): Boolean {
        val u = r.uppercase()
        return u.isBlank() || u.contains("NO DATA") || u.contains("BUS INIT") ||
            u.contains("CAN ERROR") || u.contains("BUFFER FULL") || u.contains("?")
 fun ask(cmd: String, tries: Int = 3, timeoutMs: Int = 3500): String {   }

    /**fun ask(cmd: String, tries: Int = 3, timeoutMs: Long = 3500): String {
     * Dërgon komandën me përsëritje dhe timeout më të gjatë.
     * Në tension të ulët modulet përgjigjen ngadalë, prandaj 3 provë + pauzë.
     */
    fun ask(cmd: String, tries: Int = 3, timeoutMs: Int = 3500): String {
        var last = ""
        for (i in 1..tries) {
            last = CarLink.send(cmd, timeoutMs + (i - 1) * 1500)
            // 7F xx 78 = moduli po punon, prit
            if (last.uppercase().contains("7F") && last.uppercase().contains(" 78")) {
                Thread.sleep(800)
                last = CarLink.send("", 3000)
            }
            if (!bad(last)) return last
            Thread.sleep(400L * i)
        }
        return last
    }

    /** Zgjon autobusin (disa module flenë në ignition ON pa motor). */
    fun wake(addrHex: String): Boolean {
        CarLink.send("ATSH 6F1", 1000)
        val r = ask("3E 00", 2, 3000)
        return !bad(r)
    }
}
