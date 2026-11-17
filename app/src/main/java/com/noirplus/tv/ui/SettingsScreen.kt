package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.BuildConfig
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.SUB_MAX
import com.noirplus.tv.data.SUB_MIN
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.Updater
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title.uppercase(), color = Col.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Col.Surface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun Note(text: String) = Text(text, color = Col.Sub, fontSize = 12.sp, lineHeight = 16.sp)

/** Fila que abre algo o ejecuta una acción, con valor a la derecha y flecha. */
@Composable
private fun LinkRow(label: String, value: String? = null, key: String, danger: Boolean = false, onClick: () -> Unit) {
    Focusable(Modifier.fillMaxWidth(), key = key, shape = RoundedCornerShape(10.dp), focusScale = 1f, onClick = onClick) { focused ->
        Row(
            Modifier.fillMaxWidth().background(if (focused) Color.White else Color.Transparent).padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (focused) Color.Black else if (danger) Col.Error else Col.Text, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            value?.let { Text(it, color = if (focused) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 13.sp, maxLines = 1) }
            Text("  ›", color = if (focused) Color.Black else Col.Sub, fontSize = 16.sp)
        }
    }
}

@Composable
fun SettingsContent() {
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val d = LocalDims.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes = prefs.changes.intValue
    val info by produceState<String?>(null) { value = Repo.source?.accountInfo() ?: "No se pudo comprobar la cuenta" }
    var nameEdit by remember { mutableStateOf(prefs.name) }
    var pinDialog by remember { mutableStateOf<String?>(null) } // "set", "unlock", "remove"
    var updStatus by remember { mutableStateOf<String?>(null) }
    var updRelease by remember { mutableStateOf<Updater.Release?>(null) }
    var updProgress by remember { mutableStateOf<Float?>(null) }

    var refreshStatus by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val lastUpd = remember(Repo.dataVersion.intValue) {
        val mins = (System.currentTimeMillis() - Repo.loadedAt) / 60_000
        when {
            mins < 1 -> "Ahora mismo"
            mins < 60 -> "Hace $mins min"
            mins < 60 * 24 -> "Hace ${mins / 60} h"
            else -> "Hace ${mins / 1440} días"
        }
    }

    fun refresh(kinds: List<Kind>?) {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale("es", "ES"))
            val done = mutableListOf<String>()
            Repo.refresh(kinds) { st ->
                refreshStatus = when {
                    st.error != null -> "Error en ${kindPlural(st.kind)}: ${st.error}"
                    st.count == null -> (done + "actualizando ${kindPlural(st.kind)}…").joinToString("  ·  ")
                    else -> {
                        done += "${kindPlural(st.kind)} ✓"
                        done.joinToString("  ·  ")
                    }
                }
            }
            refreshStatus = "Listo: " + done.joinToString("  ·  ")
            refreshing = false
        }
    }

    fun checkUpdates() {
        updStatus = "Buscando…"
        scope.launch {
            val r = Updater.latest()
            when {
                r == null -> updStatus = "No se pudo comprobar. Revisa la conexión."
                r.versionCode <= Updater.currentCode -> updStatus = "Tienes la última versión."
                else -> {
                    updRelease = r
                    updStatus = "Disponible: ${r.name}"
                }
            }
        }
    }

    fun installUpdate(rel: Updater.Release) {
        if (updProgress != null) return
        updProgress = 0f
        updStatus = "Descargando…"
        scope.launch {
            try {
                val f = Updater.download(ctx, rel) { p -> updProgress = p }
                updStatus = "Verificado. Abriendo el instalador…"
                if (!Updater.install(ctx, f)) updStatus = "Permite «Instalar apps desconocidas» para Noir+ y vuelve a pulsar."
            } catch (t: Throwable) {
                updStatus = t.message ?: "Error al descargar"
            } finally {
                updProgress = null
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = d.edge, end = d.edge, top = 12.dp, bottom = d.bottomPad)
                .then(if (d.compact) Modifier else Modifier.widthIn(max = 860.dp)),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (changes < 0) Spacer(Modifier)

            Section("Perfil") {
                SettingRow("Tu nombre", "Se usa para el saludo de Inicio.") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = nameEdit, onValueChange = { nameEdit = it }, singleLine = true,
                            modifier = Modifier.weight(1f), colors = fieldColors(),
                        )
                        if (nameEdit.trim() != prefs.name) {
                            ActionButton("Guardar", primary = true, modifier = Modifier.padding(start = 8.dp)) { prefs.name = nameEdit }
                        }
                    }
                }
            }

            Section("Cuenta") {
                Column {
                    Text(if (prefs.type == "xtream") "Xtream Codes · ${prefs.user}" else "Lista M3U", color = Col.Text, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                    if (prefs.type == "xtream") Note(prefs.server)
                    Note(info ?: "Comprobando cuenta…")
                }
                Column {
                    LinkRow("Cambiar de lista", key = "set-logout", danger = true) {
                        prefs.clearAccount()
                        Repo.rebuild()
                        nav.reset(Screen.Setup)
                    }
                }
            }

            Section("Actualizar listas") {
                Note("Tu proveedor añade contenido a diario. Actualiza para traer lo último sin cerrar la app.")
                Column {
                    LinkRow("Actualizar todo", lastUpd, key = "upd-all") { refresh(null) }
                    LinkRow("Solo películas", key = "upd-mov") { refresh(listOf(Kind.MOVIE)) }
                    LinkRow("Solo series", key = "upd-ser") { refresh(listOf(Kind.SERIES)) }
                    LinkRow("Solo canales en directo", key = "upd-live") { refresh(listOf(Kind.LIVE)) }
                }
                refreshStatus?.let { Note(it) }
            }

            Section("Plataformas") {
                Note("Cada película y serie muestra en qué plataforma está en España. Datos de TMDB (themoviedb.org) y JustWatch. Este producto usa la API de TMDB pero no está respaldado ni certificado por TMDB.")
            }

            Section("Reproductor") {
                Note("Botones de acceso rápido junto a la ruedita del reproductor. Lo que no actives queda dentro de la ruedita.")
                QuickToggle("Audio (idioma)", "audio")
                QuickToggle("Subtítulos (elegir pista)", "cc")
                QuickToggle("Opciones de subtítulos", "subopts")
                QuickToggle("Velocidad", "speed")
                QuickToggle("Formato de imagen", "format")
            }

            Section("Aspecto") {
                val sortOpts = listOf(SortMode.RECENT, SortMode.RATING, SortMode.AZ, SortMode.DEFAULT)
                ChoiceRow("Orden de las carpetas", listOf("Recientes", "Valoradas", "A–Z", "Original"),
                    sortOpts.indexOf(prefs.defaultSort).coerceAtLeast(0), "ds",
                    note = "Cómo se ordena al abrir «Ver todo» o «Todas». Siempre puedes cambiarlo dentro con el botón de ordenar.") {
                    prefs.defaultSort = sortOpts[it]
                }
                val limits = listOf(25, 50, 75, 100)
                ChoiceRow("Títulos por fila", limits.map { it.toString() }, limits.indexOf(prefs.rowLimit).coerceAtLeast(1), "rl",
                    note = "Cuántos se muestran en cada fila. El resto, en «Ver todo». Menos = más fluido en la tele.") {
                    prefs.rowLimit = limits[it]
                }
                ChoiceRow("Columnas en las carpetas", listOf("2", "3", "4"), prefs.gridCols - 2, "gc",
                    note = "En el móvil, al abrir «Ver todo» o «Todas». También se cambia dentro de la propia lista.") { prefs.gridCols = it + 2 }
            }

            Section("Subtítulos") {
                SubtitlePreview(prefs.subPos, prefs.subOffset, prefs.subSizeSp, prefs.subBg == 1, prefs.subFont,
                    Modifier.fillMaxWidth().aspectRatio(16f / 6.5f))
                ChoiceRow("Posición", SubPositions, prefs.subPos, "sp") { prefs.subPos = it }
                StepperRow("Altura", "${if (prefs.subOffset > 0) "+" else ""}${prefs.subOffset}", { prefs.subOffset -= 4 }, { prefs.subOffset += 4 }, "so",
                    note = "Ajuste fino dentro de la posición elegida.")
                StepperRow("Retardo", fmtDelay(prefs.subDelayMs), { prefs.subDelayMs -= 100 }, { prefs.subDelayMs += 100 }, "sd",
                    note = "Entre −2 y +2 s. Positivo = salen más tarde.")
                StepperRow("Tamaño", "${prefs.subSizeSp}", { prefs.subSizeSp -= 1 }, { prefs.subSizeSp += 1 }, "ss",
                    note = "Entre $SUB_MIN y $SUB_MAX.")
                SettingRow("Tipografía") { FontPicker(prefs.subFont, "sf") { prefs.subFont = it } }
                ChoiceRow("Fondo", listOf("Sombra", "Caja semitransparente"), prefs.subBg, "sb") { prefs.subBg = it }
            }

            Section("Reproducción") {
                ChoiceRow("Siguiente episodio automático", listOf("Sí", "No"), if (prefs.autoNext) 0 else 1, "an") { prefs.autoNext = it == 0 }
                ChoiceRow("Formato de canales", listOf("HLS", "TS"), if (prefs.liveFormat == "ts") 1 else 0, "lf",
                    note = "Si un canal no arranca, prueba el otro.") { prefs.liveFormat = if (it == 1) "ts" else "m3u8" }
                LinkRow("Borrar «Continuar viendo»", key = "set-clearh") { prefs.clearHistory() }
            }

            Section("Contenido y control parental") {
                Column {
                    LinkRow("Ocultar y proteger carpetas", key = "set-folders") { nav.push(Screen.Folders) }
                    if (prefs.pin.isEmpty()) {
                        LinkRow("Crear PIN", "Sin PIN", key = "set-pin") { pinDialog = "set" }
                    } else {
                        if (prefs.unlocked) LinkRow("Volver a bloquear", "Desbloqueado", key = "set-lock") { prefs.unlocked = false }
                        else LinkRow("Desbloquear contenido protegido", "Bloqueado", key = "set-unlock") { pinDialog = "unlock" }
                        LinkRow("Cambiar PIN", key = "set-pinch") { pinDialog = "set" }
                        LinkRow("Quitar PIN", key = "set-pinrm", danger = true) { pinDialog = "remove" }
                    }
                }
            }

            Section("Registro de errores") {
                val ev = com.noirplus.tv.data.ErrorLog.version.intValue
                val n = remember(ev) { com.noirplus.tv.data.ErrorLog.count() }
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Note(if (n == 0) "No hay errores registrados." else "$n errores registrados. Cópialos o compártelos para revisarlos. No incluyen tu usuario, contraseña ni servidor.")
                if (n > 0) {
                    LinkRow("Copiar al portapapeles", key = "err-copy") {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Noir+ errores", com.noirplus.tv.data.ErrorLog.read()))
                        android.widget.Toast.makeText(ctx, "Copiado", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    LinkRow("Compartir", key = "err-share") {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_SUBJECT, "Noir+ · registro de errores")
                            .putExtra(android.content.Intent.EXTRA_TEXT, com.noirplus.tv.data.ErrorLog.read())
                        runCatching { ctx.startActivity(android.content.Intent.createChooser(i, "Compartir registro").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    if (!LocalDims.current.compact) {
                        // En la tele: verlo en pantalla (últimos errores)
                        Text(com.noirplus.tv.data.ErrorLog.read().takeLast(1500), color = Col.Sub, fontSize = 10.sp, lineHeight = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    LinkRow("Borrar registro", key = "err-clear", danger = true) { com.noirplus.tv.data.ErrorLog.clear() }
                }
            }

            Section("Actualizaciones") {
                val rel = updRelease
                Column {
                    if (rel == null) LinkRow("Buscar actualizaciones", BuildConfig.VERSION_NAME, key = "set-upd") { checkUpdates() }
                    else LinkRow("Descargar e instalar", rel.name, key = "set-updi") { installUpdate(rel) }
                }
                updStatus?.let { Note(it) }
                updProgress?.let {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(it).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White))
                    }
                }
                Note("Cada versión se comprueba con su huella SHA-256 antes de instalarse.")
            }

            Text("Noir+  ·  ${BuildConfig.VERSION_NAME}", color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }

        pinDialog?.let { mode ->
            PinDialog(
                title = when (mode) {
                    "set" -> "Nuevo PIN (4 cifras)"
                    "remove" -> "PIN actual para quitarlo"
                    else -> "Introduce el PIN"
                },
                onCancel = { pinDialog = null },
            ) { pin ->
                when (mode) {
                    "set" -> { prefs.pin = pin; prefs.unlocked = false; null }
                    "remove" -> if (pin == prefs.pin) { prefs.pin = ""; prefs.unlocked = false; null } else "PIN incorrecto"
                    else -> if (pin == prefs.pin) { prefs.unlocked = true; null } else "PIN incorrecto"
                }.also { if (it == null) pinDialog = null }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Col.Surface2,
    focusedTextColor = Col.Text,
    unfocusedTextColor = Col.Text,
    focusedLabelColor = Col.Text,
    unfocusedLabelColor = Col.Sub,
    cursorColor = Col.Text,
)

/** Diálogo de PIN. onDone devuelve un error o null si es correcto. */
@Composable
fun PinDialog(title: String, onCancel: () -> Unit, onDone: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler { onCancel() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(16.dp)).background(Col.Surface2).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = Col.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            err?.let { Text(it, color = Col.Error, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("Aceptar", primary = true, modifier = Modifier.weight(1f)) {
                    err = if (pin.length != 4) "Tienen que ser 4 cifras" else onDone(pin)
                }
                DialogButton("Cancelar", modifier = Modifier.weight(1f)) { onCancel() }
            }
        }
    }
}

@Composable
private fun QuickToggle(label: String, k: String) {
    val prefs = Repo.prefs
    val on = remember(prefs.changes.intValue) { prefs.quick(k) }
    ChoiceRow(label, listOf("Botón", "En la ruedita"), if (on) 0 else 1, "q-$k") { prefs.setQuick(k, it == 0) }
}

