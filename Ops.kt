package com.filemanager.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object Clip {
    var files by mutableStateOf<List<File>>(emptyList())
    var move by mutableStateOf(false)
    fun clear() { files = emptyList() }
}

enum class Mode { NONE, MENU, RENAME, DELETE, NEW }

class FileOps(
    val ctx: Context,
    val prefs: Prefs,
    val scope: CoroutineScope,
    val onChanged: () -> Unit
) {
    var target by mutableStateOf<File?>(null)
    var mode by mutableStateOf(Mode.NONE)
    var dir by mutableStateOf<File?>(null)

    fun close() { mode = Mode.NONE; target = null }

    fun show(f: File) { target = f; mode = Mode.MENU }

    private fun run(msg: String? = null, block: () -> Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching(block).getOrDefault(false) }
            Fs.invalidate()
            if (!ok) Toast.makeText(ctx, "Operation failed", Toast.LENGTH_SHORT).show()
            else if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            onChanged()
        }
    }

    fun rename(f: File, name: String) = run { f.renameTo(File(f.parentFile, name)) }
    fun delete(f: File) = run { f.deleteRecursively() }
    fun mkdir(parent: File, name: String) = run { File(parent, name).mkdirs() }
    fun paste(into: File) {
        val files = Clip.files; val mv = Clip.move
        Clip.clear()
        run { Fs.paste(files, mv, into) }
    }
}

@Composable
fun FileOpsDialogs(ops: FileOps) {
    val fm = LocalFm.current
    val f = ops.target

    @Composable
    fun Item(label: String, onClick: () -> Unit) {
        Text(
            label, color = fm.text, fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp)
        )
    }

    when (ops.mode) {
        Mode.MENU -> if (f != null) AlertDialog(
            onDismissRequest = { ops.close() },
            containerColor = fm.card,
            title = { Text(f.name, color = fm.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (f.isFile) {
                        Item("Open") { Fs.open(ops.ctx, f); ops.close() }
                        Item("Share") { Fs.share(ops.ctx, f); ops.close() }
                    }
                    Item("Copy") { Clip.files = listOf(f); Clip.move = false; ops.close() }
                    Item("Move") { Clip.files = listOf(f); Clip.move = true; ops.close() }
                    Item("Rename") { ops.mode = Mode.RENAME }
                    Item(if (f.path in ops.prefs.favorites) "Remove from favorites" else "Add to favorites") {
                        ops.prefs.toggleFav(f.path); ops.close()
                    }
                    Item("Delete") { ops.mode = Mode.DELETE }
                }
            },
            confirmButton = {}
        )

        Mode.RENAME -> if (f != null) {
            var name by remember(f) { mutableStateOf(f.name) }
            AlertDialog(
                onDismissRequest = { ops.close() },
                containerColor = fm.card,
                title = { Text("Rename", color = fm.text) },
                text = { NameField(name) { name = it } },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) ops.rename(f, name.trim()); ops.close()
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { ops.close() }) { Text("Cancel") } }
            )
        }

        Mode.NEW -> {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { ops.close() },
                containerColor = fm.card,
                title = { Text("New folder", color = fm.text) },
                text = { NameField(name) { name = it } },
                confirmButton = {
                    TextButton(onClick = {
                        val d = ops.dir
                        if (name.isNotBlank() && d != null) ops.mkdir(d, name.trim()); ops.close()
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { ops.close() }) { Text("Cancel") } }
            )
        }

        Mode.DELETE -> if (f != null) AlertDialog(
            onDismissRequest = { ops.close() },
            containerColor = fm.card,
            title = { Text("Delete?", color = fm.text) },
            text = { Text(f.name, color = fm.sub) },
            confirmButton = { TextButton(onClick = { ops.delete(f); ops.close() }) { Text("Delete", color = Color(0xFFFF5252)) } },
            dismissButton = { TextButton(onClick = { ops.close() }) { Text("Cancel") } }
        )

        Mode.NONE -> {}
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    val fm = LocalFm.current
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fm.bg, unfocusedContainerColor = fm.bg,
            focusedTextColor = fm.text, unfocusedTextColor = fm.text
        )
    )
}
