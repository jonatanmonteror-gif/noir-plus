package com.noirplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.desktop.BuildInfo
import com.noirplus.desktop.data.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val changes = Store.changes.intValue
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Store.name) }
    val info by produceState<String?>(null, Repo.dataVersion.intValue) { value = runCatching { Repo.source?.accountInfo() }.getOrNull() }
    var updStatus by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMsg by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp).widthIn(max = 820.dp)) {
        Text("Ajustes", color = Col.Text, fontSize = 34.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 18.dp))

        Card("Perfil") {
            Field("Tu nombre", name) { name = it }
            if (name.trim() != Store.name) NButton("Guardar", primary = true) { Store.name = name }
        }
        Card("Cuenta") {
            Row2("Tipo", if (Store.type == "xtream") "Xtream Codes" else "Lista M3U")
            if (Store.type == "xtream") { Row2("Servidor", Store.server); Row2("Usuario", Store.user) }
            info?.let { Row2("Estado", it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton(if (refreshing) "Actualizando…" else "Actualizar listas", primary = true) {
                    if (!refreshing) {
                        refreshing = true
                        refreshMsg = "Descargando las listas de tu servidor…"
                        scope.launch {
                            refreshMsg = runCatching { Repo.refreshNow() }.fold({ it }, { "No se pudo actualizar: " + friendlyError(it) })
                            refreshing = false
                        }
                    }
                }
                NButton("Cambiar de cuenta") { Store.logout(); Repo.rebuild() }
            }
            refreshMsg?.let { Text(it, color = Col.Sub, fontSize = 13.sp) }
        }
        Card("Sincronización") {
            val sv = Sync.version.intValue
            var syncUrl by remember { mutableStateOf(Sync.url.removePrefix("https://")) }
            var devName by remember { mutableStateOf(Sync.deviceName) }
            LaunchedEffect(Unit) { Sync.request(0) }
            Text("Recuerda lo que has visto y Mi lista entre Windows, la tele y el móvil con la misma cuenta. Se guarda en tu propio servidor.",
                color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
            Field("Dirección del servidor (tu dominio)", syncUrl) { syncUrl = it }
            if (syncUrl.trim() != Sync.url.removePrefix("https://")) NButton("Guardar dirección", primary = true) {
                Sync.setUrl(syncUrl); syncUrl = Sync.url.removePrefix("https://")
            }
            Field("Nombre de este aparato", devName) { devName = it.take(40) }
            if (devName.trim() != Sync.deviceName) NButton("Guardar nombre", primary = true) { Sync.deviceName = devName }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sincronizar este aparato", color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Seg(listOf("Sí" to "1", "No" to "0"), { if (Sync.enabled) "1" else "0" }) { Sync.enabled = it == "1" }
            }
            if (Sync.enabled) {
                listOf("progress" to "Continuar viendo", "favs" to "Mi lista").forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Seg(listOf("Sí" to "1", "No" to "0"), { if (Sync.section(k)) "1" else "0" }) { Sync.setSection(k, it == "1") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NButton("Sincronizar ahora") { Sync.request(0) }
                    Sync.status.value?.let { Text(it, color = Col.Sub, fontSize = 13.sp) }
                }
                val devs = Sync.devices.value
                if (devs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Aparatos", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Un aparato excluido no comparte ni recibe nada hasta que lo vuelvas a incluir desde otro.", color = Col.Sub, fontSize = 12.sp)
                    devs.forEach { dv ->
                        val kindTxt = when (dv.kind) { "tv" -> "Tele"; "movil" -> "Móvil"; "windows" -> "Windows"; else -> "" }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dv.name + if (dv.id == Sync.deviceId) " (este)" else "", color = Col.Text, fontSize = 14.sp)
                                Text(listOf(kindTxt, if (dv.blocked) "Excluido" else "Incluido").filter { it.isNotEmpty() }.joinToString(" · "),
                                    color = Col.Sub, fontSize = 12.sp)
                            }
                            if (dv.id != Sync.deviceId) NButton(if (dv.blocked) "Incluir" else "Excluir") { Sync.setBlocked(dv.id, !dv.blocked) }
                        }
                    }
                }
            }
            if (sv < 0) Spacer(Modifier)
        }
        Card("Pantalla") {
            Text("Cómo se abre Noir+", color = Col.Text, fontSize = 14.sp)
            Seg(listOf("Pantalla completa" to "full", "Inmersiva" to "immersive", "Ventana" to "window"), { Store.windowMode }) { Store.windowMode = it }
            Text("Pantalla completa: sin barra de título. Inmersiva: oculta también la barra de Windows. Ventana: normal, con su barra.",
                color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Card("Subtítulos") {
            Text("Así se verán en todo lo que reproduzcas. También se pueden cambiar desde el reproductor.", color = Col.Sub, fontSize = 12.sp)
            SubPreview(changes)
            SubStyleControls(changes, compact = false) {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Retardo por defecto", color = Col.Text, fontSize = 13.sp, modifier = Modifier.width(160.dp))
                Stepper(String.format("%+.1f s", Store.subDelayMs / 1000f), { Store.subDelayMs -= 100 }, { Store.subDelayMs += 100 })
            }
        }
        Card("Valoraciones") {
            Text("La nota de cada película y serie (★ 7,8) es la de IMDb: sus datos públicos se descargan a este PC una vez por semana. " +
                "Para saber qué título de IMDb es cada uno se usa TMDB.", color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Card("Al abrir Películas y Series") {
            Text("Qué vista sale primero. Mientras navegas puedes cambiarla arriba; al volver a entrar se usa esta.",
                color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
            listOf(Kind.MOVIE to "Películas", Kind.SERIES to "Series").forEach { (k, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Seg(listOf("Recién subido" to "new", "Plataformas" to "plat", "Géneros" to "genre"), { Store.startMode(k) }) {
                        Store.setStartMode(k, it)
                    }
                }
            }
        }
        Card("Próximos estrenos") {
            Text("Cuántas columnas de estrenos se ven a la vez. Se queda guardado.", color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Columnas", color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Seg(listOf("2 columnas" to "2", "3 columnas" to "3"), { Store.upcomingColumns().toString() }) {
                    Store.setUpcomingColumns(it.toInt())
                }
            }
        }
        Card("Plataformas") {
            Text("Las plataformas (Netflix, Disney+…), los géneros y las fechas de estreno vienen de TMDB. " +
                "Este producto usa la API de TMDB pero no está respaldado ni certificado por TMDB.", color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Card("Reproducción") {
            Text("Formato de los canales en directo", color = Col.Text, fontSize = 14.sp)
            Seg(listOf("TS" to "ts", "HLS (m3u8)" to "m3u8"), { Store.liveFormat }) { Store.liveFormat = it; Repo.rebuild() }
            Text("Si un canal no carga, prueba el otro formato.", color = Col.Sub, fontSize = 12.sp)

            Spacer(Modifier.height(6.dp))
            Text("Botones del reproductor", color = Col.Text, fontSize = 14.sp)
            Text("Cada opción va en un sitio: como botón en pantalla o dentro de la ruedita, nunca en los dos.", color = Col.Sub, fontSize = 12.sp)
            QuickOptions.forEach { (k, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Seg(listOf("Botón" to "1", "En la ruedita" to "0"), { if (Store.quick(k)) "1" else "0" }) { Store.setQuick(k, it == "1") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Atajos en el reproductor: Espacio pausa · ←/→ 10 s · ↑/↓ volumen · F pantalla completa · S subtítulos · A audio · Esc salir.",
                color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Card("Actualizaciones") {
            Row2("Versión instalada", BuildInfo.VERSION)
            val p = progress
            if (p != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Color.White))
                }
            } else {
                val r = release
                if (r == null) NButton("Buscar actualizaciones") {
                    updStatus = "Buscando…"
                    scope.launch {
                        val found = runCatching { Updater.check() }
                        release = found.getOrNull()
                        updStatus = when {
                            found.isFailure -> "No se pudo comprobar: " + friendlyError(found.exceptionOrNull()!!)
                            release == null -> "Ya tienes la última versión."
                            else -> "Hay una versión nueva: ${release!!.version}"
                        }
                    }
                } else NButton("Descargar e instalar ${r.version}", primary = true) {
                    progress = 0f
                    scope.launchCatching({ updStatus = it; progress = null }) { Updater.install(r) { progress = it } }
                }
            }
            updStatus?.let { Text(it, color = Col.Sub, fontSize = 13.sp) }
            NButton("Novedades de cada versión") { scope.launch { NotesHost.show("Novedades de Noir+ para Windows", Updater.changelog()) } }
            Text("Cada versión se comprueba con su huella SHA-256 antes de instalarse.", color = Col.Sub, fontSize = 12.sp)
        }
        Card("Acerca de") {
            Text(Startup.summary(), color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp)
            Text("Noir+ para Windows. El vídeo se reproduce con VLC (debe estar instalado, versión de 64 bits).",
                color = Col.Sub, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (changes < 0) Spacer(Modifier)
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = Col.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Col.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Col.Sub, fontSize = 14.sp, modifier = Modifier.width(170.dp))
        Text(value, color = Col.Text, fontSize = 14.sp)
    }
}

@Composable
fun Seg(options: List<Pair<String, String>>, selectedOf: () -> String, onSelect: (String) -> Unit) {
    // Se lee aquí dentro para que la opción elegida se marque al momento
    Store.changes.intValue
    val selected = selectedOf()
    Row(Modifier.clip(RoundedCornerShape(50)).background(Col.Surface2).padding(3.dp)) {
        options.forEach { (label, v) ->
            val s = v == selected
            Text(label, color = if (s) Color.Black else Col.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (s) Color.White else Color.Transparent)
                    .clickable { onSelect(v) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp, vertical = 7.dp))
        }
    }
}
