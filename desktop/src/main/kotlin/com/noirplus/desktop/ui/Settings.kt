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
                NButton("Actualizar listas", primary = true) { reloadLists() }
                NButton("Cambiar de cuenta") { Store.logout(); Repo.rebuild() }
            }
        }
        Card("Reproducción") {
            Text("Formato de los canales en directo", color = Col.Text, fontSize = 14.sp)
            Seg(listOf("TS" to "ts", "HLS (m3u8)" to "m3u8"), Store.liveFormat) { Store.liveFormat = it; Repo.rebuild() }
            Text("Si un canal no carga, prueba el otro formato.", color = Col.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("Tamaño de los subtítulos", color = Col.Text, fontSize = 14.sp)
            Seg(listOf("Pequeño" to "70", "Normal" to "100", "Grande" to "130", "Muy grande" to "160"), Store.subScale.toString()) {
                Store.subScale = it.toInt()
            }
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
            Text("Cada versión se comprueba con su huella SHA-256 antes de instalarse.", color = Col.Sub, fontSize = 12.sp)
        }
        Card("Acerca de") {
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
fun Seg(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Col.Surface2).padding(3.dp)) {
        options.forEach { (label, v) ->
            val s = v == selected
            Text(label, color = if (s) Color.Black else Col.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (s) Color.White else Color.Transparent)
                    .clickable { onSelect(v) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp, vertical = 7.dp))
        }
    }
}
