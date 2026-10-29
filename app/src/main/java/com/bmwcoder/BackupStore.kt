package com.bmwcoder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupStore {
    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "backups")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun save(ctx: Context, module: String, data: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = module.replace(Regex("[^A-Za-z0-9]"), "_")
        val f = File(dir(ctx), stamp + "_" + safe + ".txt")
        f.writeText("module=$module\ndate=$stamp\ndata=$data\n")
        return f
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.isFile }?.sortedByDescending { it.name } ?: emptyList()

    fun read(f: File): String = f.readText()

    fun delete(f: File) {
        f.delete()
    }
}
