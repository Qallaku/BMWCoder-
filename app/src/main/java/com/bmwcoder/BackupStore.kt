package com.bmwcoder

import android.content.Context
import java.io.File

object BackupStore {
    data class Item(
        val file: File,
        val module: String,
        val time: Long,
        val original: Boolean,
        val data: String
    )

    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "backups_v2")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun parse(f: File): Item? {
        return try {
            val m = f.readLines().mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
            }.toMap()
            Item(
                f,
                m["module"] ?: "?",
                (m["time"] ?: "0").toLong(),
                m["original"] == "true",
                m["data"] ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }

    fun all(ctx: Context): List<Item> =
        (dir(ctx).listFiles() ?: emptyArray())
            .mapNotNull { parse(it) }
            .sortedByDescending { it.time }

    fun modules(ctx: Context): List<String> =
        all(ctx).map { it.module }.distinct().sorted()

    fun forModule(ctx: Context, module: String): List<Item> =
        all(ctx).filter { it.module == module }

    fun save(ctx: Context, module: String, data: String): Item {
        val first = forModule(ctx, module).isEmpty()
        val now = System.currentTimeMillis()
        val safe = module.replace(Regex("[^A-Za-z0-9]"), "_")
        val f = File(dir(ctx), "${now}_$safe.txt")
        f.writeText(
            "module=$module\ntime=$now\noriginal=$first\n" +
                "data=${data.replace("\n", " ")}\n"
        )
        return Item(f, module, now, first, data)
    }
    fun delete(item: Item): Boolean {
        if (item.original) return false
        return item.file.delete()
    }
}
    
    }
}
