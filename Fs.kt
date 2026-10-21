package com.filemanager.app

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

enum class Cat(val label: String, val exts: Set<String>) {
    DOCS("Docs", setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "md", "epub", "json", "xml")),
    IMAGES("Images", setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")),
    VIDEOS("Videos", setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv")),
    MUSIC("Music", setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "amr")),
    ARCHIVES("Archives", setOf("zip", "rar", "7z", "tar", "gz", "bz2")),
    APKS("APKs", setOf("apk", "apks", "xapk"))
}

object Fs {
    val root: File get() = Environment.getExternalStorageDirectory()

    @Volatile
    private var cache: List<File>? = null

    fun invalidate() { cache = null }

    /** All visible files on shared storage (cached until something changes). */
    fun index(): List<File> {
        cache?.let { return it }
        val out = ArrayList<File>()
        root.walkTopDown()
            .onEnter { d ->
                !d.name.startsWith(".") &&
                        !d.path.endsWith("/Android/data") &&
                        !d.path.endsWith("/Android/obb")
            }
            .forEach { if (it.isFile && !it.name.startsWith(".")) out.add(it) }
        cache = out
        return out
    }

    fun catOf(f: File): Cat? {
        if (!f.isFile) return null
        val e = f.extension.lowercase()
        return Cat.values().firstOrNull { e in it.exts }
    }

    fun byCat(c: Cat) = index().filter { it.extension.lowercase() in c.exts }
        .sortedByDescending { it.lastModified() }

    fun recent() = index().sortedByDescending { it.lastModified() }.take(200)

    fun search(q: String) = index().filter { it.name.contains(q, ignoreCase = true) }
        .sortedByDescending { it.lastModified() }.take(300)

    fun under(paths: List<String>) = index()
        .filter { f -> paths.any { f.path.startsWith("$it/") } }
        .sortedByDescending { it.lastModified() }

    fun rel(vararg p: String): String = p.map { File(root, it) }.firstOrNull { it.exists() }?.path
        ?: File(root, p.first()).path

    fun mime(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    fun open(ctx: Context, f: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime(f))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, f: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime(f)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't share this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var n = 1
        while (f.exists()) { f = File(dir, "$base ($n)$ext"); n++ }
        return f
    }

    fun paste(files: List<File>, move: Boolean, dir: File): Boolean {
        var ok = true
        for (f in files) {
            if (!f.exists()) continue
            if (f.isDirectory && (dir.path == f.path || dir.path.startsWith(f.path + "/"))) { ok = false; continue }
            val dest = unique(dir, f.name)
            ok = if (move) {
                f.renameTo(dest) || (f.copyRecursively(dest) && f.deleteRecursively())
            } else f.copyRecursively(dest)
        }
        return ok
    }
}
