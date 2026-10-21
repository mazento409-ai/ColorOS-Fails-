@file:OptIn(ExperimentalFoundationApi::class)

package com.filemanager.app

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Screen {
    object Browse : Screen()
    object Recent : Screen()
    object Search : Screen()
    object Favorites : Screen()
    data class Category(val cat: Cat) : Screen()
    data class Folder(val path: String) : Screen()
    data class Multi(val title: String, val paths: List<String>) : Screen()
}

val LocalLoader = staticCompositionLocalOf<ImageLoader> { error("no loader") }

fun Cat.color(): Color = when (this) {
    Cat.DOCS -> IcColor.docs
    Cat.IMAGES -> IcColor.images
    Cat.VIDEOS -> IcColor.videos
    Cat.MUSIC -> IcColor.music
    Cat.ARCHIVES -> IcColor.archives
    Cat.APKS -> IcColor.apks
}

fun Cat.icon(): ImageVector = when (this) {
    Cat.DOCS -> Icons.Rounded.Description
    Cat.IMAGES -> Icons.Rounded.Image
    Cat.VIDEOS -> Icons.Rounded.Videocam
    Cat.MUSIC -> Icons.Rounded.MusicNote
    Cat.ARCHIVES -> Icons.Rounded.FolderZip
    Cat.APKS -> Icons.Rounded.Android
}

// ---------- shared pieces ----------

@Composable
fun IconBox(color: Color, icon: ImageVector, size: Int = 48, radius: Int = 14) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(radius.dp)).background(color),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp)) }
}

@Composable
fun Header(title: String, subtitle: String? = null, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val fm = LocalFm.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = fm.text)
        } else Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fm.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = fm.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun Thumb(f: File) {
    val cat = Fs.catOf(f)
    when {
        f.isDirectory -> IconBox(IcColor.folder, Icons.Rounded.Folder)
        cat == Cat.IMAGES || cat == Cat.VIDEOS -> Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cat.color()),
            contentAlignment = Alignment.Center
        ) {
            Icon(cat.icon(), null, tint = Color.White, modifier = Modifier.size(26.dp))
            AsyncImage(
                model = f, imageLoader = LocalLoader.current, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        cat != null -> IconBox(cat.color(), cat.icon())
        else -> IconBox(Color(0xFF8E8E93), Icons.Rounded.InsertDriveFile)
    }
}

@Composable
fun FileRow(f: File, fav: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val fm = LocalFm.current
    val ctx = LocalContext.current
    val sub = remember(f.path, f.lastModified()) {
        val d = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
        if (f.isDirectory) "${f.list()?.size ?: 0} items  ·  $d"
        else "${Formatter.formatShortFileSize(ctx, f.length())}  ·  $d"
    }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = fm.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = fm.sub, fontSize = 12.sp, maxLines = 1)
        }
        if (fav) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF4B4B), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PasteBar(onPaste: () -> Unit) {
    val fm = LocalFm.current
    if (Clip.files.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp))
            .background(fm.card).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${if (Clip.move) "Move" else "Copy"}: ${Clip.files.first().name}",
            color = fm.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPaste) { Text("Paste here") }
        TextButton(onClick = { Clip.clear() }) { Text("Cancel") }
    }
}

// ---------- Browse ----------

@Composable
fun BrowseScreen(nav: (Screen) -> Unit) {
    val fm = LocalFm.current
    val prefs = LocalPrefs.current
    var menu by remember { mutableStateOf(false) }

    val sources = remember {
        val r = Fs.root
        val shots = listOf("Pictures/Screenshots", "DCIM/Screenshots", "Pictures/Screenrecorder", "DCIM/ScreenRecorder",
            "MIUI/ScreenRecorder", "Movies/Screenrecords", "Movies/ScreenRecorder").map { File(r, it).path }
        val rec = listOf("Recordings", "Music/Recordings", "MIUI/sound_recorder", "Sounds").map { File(r, it).path }
        val tg = listOf("Telegram", "Pictures/Telegram", "Movies/Telegram", "Android/media/org.telegram.messenger/Telegram")
            .map { File(r, it).path }
        val dual = File("/storage/emulated/999")
        buildList {
            add(Triple("Camera", Pair(IcColor.camera, Icons.Rounded.CameraAlt), Screen.Folder(Fs.rel("DCIM/Camera", "DCIM"))))
            add(Triple("Screenshots & screen recordings", Pair(IcColor.screens, Icons.Rounded.CropFree), Screen.Multi("Screenshots & screen recordings", shots)))
            add(Triple("Recorder", Pair(IcColor.recorder, Icons.Rounded.Mic), Screen.Multi("Recorder", rec)))
            if (dual.exists()) add(Triple("Dual apps", Pair(IcColor.dual, Icons.Rounded.Layers), Screen.Folder(dual.path)))
            if (tg.any { File(it).exists() }) add(Triple("Telegram", Pair(IcColor.telegram, Icons.Rounded.Send), Screen.Multi("Telegram", tg)))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = fm.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(fm.card)) {
                    listOf(ThemeMode.AUTO to "Theme: Auto", ThemeMode.LIGHT to "Theme: Light", ThemeMode.DARK to "Theme: Dark").forEach { (m, label) ->
                        DropdownMenuItem(
                            text = { Text((if (prefs.theme == m) "✓  " else "     ") + label, color = fm.text) },
                            onClick = { prefs.setTheme(m); menu = false }
                        )
                    }
                }
            }
        }
        Text("Browse", color = fm.text, fontSize = 46.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 28.dp))

        Cat.values().toList().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { c ->
                    Row(
                        Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(36.dp)).background(fm.card)
                            .clickable { nav(Screen.Category(c)) }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(c.color(), c.icon(), 50, 16)
                        Spacer(Modifier.width(14.dp))
                        Text(c.label, color = fm.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        SectionCard {
            NavRow("Downloads", { nav(Screen.Folder(Fs.rel("Download"))) }) { Icon(Icons.Rounded.Download, null, tint = fm.text, modifier = Modifier.size(34.dp)) }
            NavRow("Favorites", { nav(Screen.Favorites) }) { Icon(Icons.Rounded.FavoriteBorder, null, tint = fm.text, modifier = Modifier.size(34.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        SectionCard {
            NavRow("My phone", { nav(Screen.Folder(Fs.root.path)) }) { Icon(Icons.Rounded.PhoneAndroid, null, tint = fm.text, modifier = Modifier.size(34.dp)) }
        }

        Text("Sources", color = fm.sub, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp, top = 28.dp, bottom = 12.dp))
        SectionCard {
            sources.forEach { (label, ic, scr) ->
                NavRow(label, { nav(scr) }) { IconBox(ic.first, ic.second, 52, 16) }
            }
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(LocalFm.current.card), content = content)
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val fm = LocalFm.current
    Row(
        Modifier.fillMaxWidth().height(78.dp).clickable(onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(54.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Spacer(Modifier.width(14.dp))
        Text(label, color = fm.text, fontSize = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.ChevronRight, null, tint = fm.sub, modifier = Modifier.size(30.dp))
    }
}

// ---------- flat lists (category / recent / favorites / sources / search) ----------

@Composable
fun ListScreen(
    title: String, key: Any, onBack: (() -> Unit)?, nav: (Screen) -> Unit,
    bottomPad: Int = 24, loader: () -> List<File>
) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<File>?>(null) }
    LaunchedEffect(key, tick) { items = withContext(Dispatchers.IO) { runCatching(loader).getOrDefault(emptyList()) } }
    val ops = remember { FileOps(ctx, prefs, scope) { tick++ } }

    Column(Modifier.fillMaxSize()) {
        Header(title, null, onBack)
        FileList(items, ops, nav, prefs, bottomPad)
    }
    FileOpsDialogs(ops)
}

@Composable
private fun FileList(items: List<File>?, ops: FileOps, nav: (Screen) -> Unit, prefs: Prefs, bottomPad: Int) {
    val fm = LocalFm.current
    val ctx = LocalContext.current
    when {
        items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = fm.sub) }
        items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No files", color = fm.sub, fontSize = 16.sp) }
        else -> LazyColumn(contentPadding = PaddingValues(bottom = bottomPad.dp)) {
            items(items, key = { it.path }) { f ->
                FileRow(
                    f, f.path in prefs.favorites,
                    onClick = { if (f.isDirectory) nav(Screen.Folder(f.path)) else Fs.open(ctx, f) },
                    onLong = { ops.show(f) }
                )
            }
        }
    }
}

// ---------- folder browsing ----------

@Composable
fun FolderScreen(path: String, onBack: () -> Unit, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    val fm = LocalFm.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<File>?>(null) }
    val dir = File(path)
    LaunchedEffect(path, tick) {
        items = withContext(Dispatchers.IO) {
            dir.listFiles()?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }
    }
    val ops = remember { FileOps(ctx, prefs, scope) { tick++ } }
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Header(if (path == Fs.root.path) "My phone" else dir.name, path, onBack) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = fm.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(fm.card)) {
                    DropdownMenuItem(text = { Text("New folder", color = fm.text) }, onClick = {
                        menu = false; ops.dir = dir; ops.mode = Mode.NEW
                    })
                    if (Clip.files.isNotEmpty()) DropdownMenuItem(text = { Text("Paste", color = fm.text) }, onClick = {
                        menu = false; ops.paste(dir)
                    })
                }
            }
        }
        PasteBar { ops.paste(dir) }
        FileList(items, ops, nav, prefs, 24)
    }
    FileOpsDialogs(ops)
}

// ---------- search ----------

@Composable
fun SearchScreen(nav: (Screen) -> Unit) {
    val fm = LocalFm.current
    var q by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(300); debounced = q.trim() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Search files", color = fm.sub) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = fm.sub) },
            shape = RoundedCornerShape(32.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = fm.card, unfocusedContainerColor = fm.card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = fm.text, unfocusedTextColor = fm.text
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        if (debounced.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Type to search", color = fm.sub) }
        } else {
            Box(Modifier.weight(1f)) {
                SearchResults(debounced, nav)
            }
        }
    }
}

@Composable
private fun SearchResults(query: String, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<File>?>(null) }
    LaunchedEffect(query, tick) { items = withContext(Dispatchers.IO) { Fs.search(query) } }
    val ops = remember { FileOps(ctx, prefs, scope) { tick++ } }
    FileList(items, ops, nav, prefs, 140)
    FileOpsDialogs(ops)
}

// ---------- floating bottom bar ----------

@Composable
fun BottomBar(cur: Screen, go: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val fm = LocalFm.current
    val border = BorderStroke(1.dp, fm.divider)
    Row(
        modifier.navigationBarsPadding().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = fm.navBg, shadowElevation = 6.dp, border = border) {
            Row(Modifier.padding(6.dp)) {
                Tab("Recent", Icons.Rounded.History, cur is Screen.Recent) { go(Screen.Recent) }
                Tab("Browse", Icons.Rounded.Folder, cur is Screen.Browse) { go(Screen.Browse) }
            }
        }
        Box(
            Modifier.size(66.dp).clip(CircleShape).background(if (cur is Screen.Search) fm.navSel else fm.navBg)
                .border(border, CircleShape).clickable { go(Screen.Search) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Search, null, tint = fm.text, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun Tab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val fm = LocalFm.current
    Column(
        Modifier.width(110.dp).height(56.dp).clip(CircleShape)
            .background(if (selected) fm.navSel else Color.Transparent).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = fm.text, modifier = Modifier.size(24.dp))
        Text(label, color = fm.text, fontSize = 12.sp)
    }
}
