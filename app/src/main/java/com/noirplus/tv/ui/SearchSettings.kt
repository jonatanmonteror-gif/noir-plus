package com.noirplus.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.BuildConfig
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer

private val marks = Regex("\\p{Mn}+")
private fun norm(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

@Composable
fun SearchContent() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var index by remember { mutableStateOf<List<Pair<String, Item>>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val wantsSearch = query.trim().length >= 2

    // El catálogo completo solo se descarga la primera vez que se busca algo.
    LaunchedEffect(wantsSearch) {
        if (wantsSearch && index == null && !loading) {
            loading = true
            index = withContext(Dispatchers.Default) {
                val all = ArrayList<Item>()
                for (k in Kind.entries) all.addAll(runCatching { src.all(k) }.getOrDefault(emptyList()))
                all.map { norm(it.name) to it }
            }
            loading = false
        }
    }

    val results by produceState(emptyList<Item>(), query, index) {
        delay(250)
        val q = norm(query.trim())
        val idx = index
        value = if (q.length < 2 || idx == null) emptyList()
        else withContext(Dispatchers.Default) { idx.asSequence().filter { it.first.contains(q) }.map { it.second }.take(150).toList() }
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Busca películas, series o canales") },
            modifier = Modifier.padding(horizontal = edge).then(if (LocalDims.current.compact) Modifier.fillMaxWidth() else Modifier.width(560.dp)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                focusManager.moveFocus(FocusDirection.Down)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Col.Text,
                unfocusedBorderColor = Col.Surface2,
                focusedTextColor = Col.Text,
                unfocusedTextColor = Col.Text,
                cursorColor = Col.Accent,
            ),
        )
        when {
            !wantsSearch -> Message("Escribe al menos 2 letras")
            loading -> Message("Preparando el buscador… (la primera vez tarda un poco)")
            results.isEmpty() -> Message("Sin resultados para «${query.trim()}»")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(LocalDims.current.poster),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = edge, end = edge, top = 16.dp, bottom = LocalDims.current.bottomPad),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(results, key = { i, r -> "$i:${r.key}" }) { _, item ->
                    PosterCard(item, memKey = "search/${item.key}", width = LocalDims.current.poster, onFocus = {}) {
                        if (item.kind == Kind.LIVE) {
                            val lives = results.filter { it.kind == Kind.LIVE }
                            nav.push(
                                Screen.Player(
                                    PlayRequest(url = src.streamUrl(item), title = item.name, key = item.key, image = item.image,
                                        isLive = true, channels = lives, channelIndex = lives.indexOf(item).coerceAtLeast(0))
                                )
                            )
                        } else {
                            nav.push(Screen.Detail(item))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsContent() {
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val changes = prefs.changes.intValue
    val info by produceState<String?>(null) { value = Repo.source?.accountInfo() ?: "No se pudo comprobar la cuenta" }
    val format = remember(changes) { prefs.liveFormat }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = edge, end = edge, top = 16.dp, bottom = LocalDims.current.bottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Cuenta", color = Col.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            if (prefs.type == "xtream") "Xtream Codes  ·  ${prefs.server}  ·  usuario ${prefs.user}" else "Lista M3U  ·  ${prefs.m3u}",
            color = Col.Sub, fontSize = 14.sp,
        )
        Text(info ?: "Comprobando cuenta…", color = Col.Sub, fontSize = 14.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Recargar contenido") { Repo.rebuild() }
            ActionButton("Cambiar de lista") {
                prefs.clearAccount()
                Repo.rebuild()
                nav.reset(Screen.Setup)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Reproducción", color = Col.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Formato de canales: " + if (format == "ts") "TS" else "HLS") {
                prefs.liveFormat = if (prefs.liveFormat == "ts") "m3u8" else "ts"
            }
            ActionButton("Borrar «Continuar viendo»") { prefs.clearHistory() }
        }
        Text(
            "Si algún canal no arranca, prueba a cambiar el formato. La app ya intenta el otro automáticamente si falla.",
            color = Col.Sub, fontSize = 13.sp,
        )

        Spacer(Modifier.height(12.dp))
        Text("Noir+  ·  versión ${BuildConfig.VERSION_NAME}", color = Col.Sub, fontSize = 13.sp)
    }
}
