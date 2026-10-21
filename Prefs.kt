package com.filemanager.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("fm_prefs", Context.MODE_PRIVATE)

    var theme by mutableStateOf(
        runCatching { ThemeMode.valueOf(sp.getString("theme", "AUTO")!!) }.getOrDefault(ThemeMode.AUTO)
    )
        private set

    var favorites by mutableStateOf<Set<String>>(sp.getStringSet("favs", emptySet())!!.toSet())
        private set

    fun setTheme(m: ThemeMode) {
        theme = m
        sp.edit().putString("theme", m.name).apply()
    }

    fun toggleFav(path: String) {
        favorites = if (path in favorites) favorites - path else favorites + path
        sp.edit().putStringSet("favs", favorites).apply()
    }
}

val LocalPrefs = staticCompositionLocalOf<Prefs> { error("no prefs") }
