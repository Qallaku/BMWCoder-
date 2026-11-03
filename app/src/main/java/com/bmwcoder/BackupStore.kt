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

    fun moduleOf(f: File): String {
        return try {
            val line = f.readLines().firstOrNull { it.startsWith("module=") }
            if (line == null) "?" else line.substring(7)
        } catch (e: Exception) {
            "?"
        }
    }

    fun modules(ctx: Context): List<String> =
        list(ctx).map { moduleOf(it) }.distinct().sorted()

    fun forModule(ctx: Context, module: String): List<File> =
        list(ctx).filter { moduleOf(it) == module }

    fun isOriginal(ctx: Context, f: File): Boolean {
        val all = forModule(ctx, moduleOf(f))
        return all.isNotEmpty() && all.last() == f
    }

    fun deleteSafe(ctx: Context, f: File): Boolean {
        if (isOriginal(ctx, f)) return false
        return f.delete()
    }

    fun pretty(f: File, lang: String): String {
        return try {
            val d = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(f.name.substring(0, 15))
            if (d == null) f.name else SimpleDateFormat("d MMM yyyy, HH:mm", Locale(lang)).format(d)
        } catch (e: Exception) {
            f.name
        }
    }
}
