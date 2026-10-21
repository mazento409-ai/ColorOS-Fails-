package com.filemanager.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        val loader = ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build()
        setContent {
            FmTheme(prefs.theme) {
                CompositionLocalProvider(LocalPrefs provides prefs, LocalLoader provides loader) {
                    Root()
                }
            }
        }
    }
}

fun hasAccess(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
fun Root() {
    val ctx = LocalContext.current
    val fm = LocalFm.current
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !fm.dark
            isAppearanceLightNavigationBars = !fm.dark
        }
    }

    var granted by remember { mutableStateOf(hasAccess(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasAccess(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess(ctx)
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(fm.bg).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Files needs access to your storage to show and manage your files.", color = fm.text, fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 30) {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    try { ctx.startActivity(i) } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    launcher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }) { Text("Grant access") }
        }
        return
    }

    val stack = remember { mutableStateListOf<Screen>(Screen.Browse) }
    val nav: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(stack.size > 1) { back() }
    val go: (Screen) -> Unit = { s ->
        stack.clear(); stack.add(Screen.Browse)
        if (s !is Screen.Browse) stack.add(s)
    }
    val cur = stack.last()
    val prefs = LocalPrefs.current

    Box(Modifier.fillMaxSize().background(fm.bg)) {
        when (cur) {
            is Screen.Browse -> BrowseScreen(nav)
            is Screen.Recent -> ListScreen("Recent", "recent", null, nav, bottomPad = 140) { Fs.recent() }
            is Screen.Search -> SearchScreen(nav)
            is Screen.Favorites -> ListScreen("Favorites", prefs.favorites, back, nav) {
                prefs.favorites.map { java.io.File(it) }.filter { it.exists() }
            }
            is Screen.Category -> ListScreen(cur.cat.label, cur.cat, back, nav) { Fs.byCat(cur.cat) }
            is Screen.Multi -> ListScreen(cur.title, cur, back, nav) { Fs.under(cur.paths) }
            is Screen.Folder -> FolderScreen(cur.path, back, nav)
        }
        if (cur is Screen.Browse || cur is Screen.Recent || cur is Screen.Search) {
            BottomBar(cur, go, Modifier.align(Alignment.BottomCenter))
        }
    }
}
