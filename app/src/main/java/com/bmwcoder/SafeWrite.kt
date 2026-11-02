package com.bmwcoder

import android.content.Context

object SafeWrite {
    const val MIN_VOLTS = 12.5

    data class Result(val ok: Boolean, val message: String)

    private fun t(ctx: Context, sq: String, en: String) =
        if (LocaleHelper.get(ctx) == "en") en else sq

    private fun voltage(): Double? {
        if (!CarLink.isConnected) return null
        val r = CarLink.send("ATRV", 3000)
        val m = Regex("([0-9]+\\.?[0-9]*)").find(r) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    fun change(
        ctx: Context,
        module: String,
        param: String,
        oldValue: String,
        newValue: String,
        sendToCar: (String) -> Boolean
    ): Result {
        val connected = CarLink.isConnected

        if (connected) {
            val v = voltage()
            if (v == null || v < MIN_VOLTS) {
                return Result(false, t(ctx,
                    "Tensioni është shumë i ulët (" + (v ?: 0.0) + " V). Duhen të paktën 12.5 V. Ndiz motorin ose lidh karikues. Asgjë nuk u ndryshua.",
                    "Voltage too low (" + (v ?: 0.0) + " V). At least 12.5 V needed. Start the engine or connect a charger. Nothing was changed."))
            }
        }

        val backupFile = try {
            BackupStore.save(ctx, module, "BEFORE|" + param + "|" + oldValue)
        } catch (e: Exception) {
            null
        }
        val verified = try {
            backupFile != null && backupFile.exists() &&
                BackupStore.read(backupFile).contains(oldValue)
        } catch (e: Exception) {
            false
        }
        if (!verified) {
            return Result(false, t(ctx,
                "Backup dështoi, prandaj ndryshimi u ANULUA. Asgjë nuk u ndryshua në makinë.",
                "Backup failed, so the change was CANCELLED. Nothing was changed in the car."))
        }

        if (!connected) {
            try {
                BackupStore.save(ctx, module + " [LOG]",
                    "DEMO|" + param + "|" + oldValue + "->" + newValue + "|sent=false")
            } catch (e: Exception) {
            }
            return Result(true, t(ctx,
                "Backup u ruajt (vlera e vjetër: " + oldValue + "). DEMO: asgjë nuk u dërgua në makinë.",
                "Backup saved (old value: " + oldValue + "). DEMO: nothing was sent to the car."))
        }

        val sent = try {
            sendToCar(newValue)
        } catch (e: Exception) {
            false
        }
        try {
            BackupStore.save(ctx, module + " [LOG]",
                "WRITE|" + param + "|" + oldValue + "->" + newValue + "|sent=" + sent)
        } catch (e: Exception) {
        }
        return if (sent) {
            Result(true, t(ctx,
                "Ndryshimi u krye. Vlera e vjetër (" + oldValue + ") është ruajtur te Backup.",
                "Change done. The old value (" + oldValue + ") is saved in Backup."))
        } else {
            Result(false, t(ctx,
                "Ndryshimi nuk u dërgua (shkrimi nuk është aktivizuar ende për këtë funksion). Backup-i u ruajt.",
                "The change was not sent (writing is not enabled yet for this function). Backup was saved."))
        }
    }
}
