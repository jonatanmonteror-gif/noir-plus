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
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.Updater
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Col.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), color = Col.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        content()
    }
}

@Composable
private fun Note(text: String) = Text(text, color = Col.Sub, fontSize = 12.sp, lineHeight = 16.sp)

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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = d.edge, end = d.edge, top = 12.dp, bottom = d.bottomPad),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (changes < 0) Spacer(Modifier)

            Section("Perfil") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameEdit, onValueChange = { nameEdit = it }, singleLine = true,
                        label = { Text("Tu nombre") },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.padding(start = 10.dp))
                    ActionButton("Guardar", primary = true) { prefs.name = nameEdit }
                }
            }

            Section("Cuenta") {
                Text(
                    if (prefs.type == "xtream") "Xtream Codes  ·  ${prefs.server}  ·  usuario ${prefs.user}" else "Lista M3U",
                    color = Col.Text, fontSize = 14.sp,
                )
                Note(info ?: "Comprobando cuenta…")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Recargar contenido") { Repo.rebuild() }
                    ActionButton("Cambiar de lista") {
                        prefs.clearAccount()
                        Repo.rebuild()
                        nav.reset(Screen.Setup)
                    }
                }
            }

            Section("Subtítulos") {
                SubtitlePreview(prefs.subPos, prefs.subOffset, prefs.subSize, prefs.subBg == 1,
                    Modifier.fillMaxWidth().widthIn(max = 520.dp).aspectRatio(16f / 7f))
                ChoiceRow("Posición", SubPositions, prefs.subPos, "sp") { prefs.subPos = it }
                StepperRow("Altura (ajuste fino)", "${prefs.subOffset}", { prefs.subOffset -= 4 }, { prefs.subOffset += 4 }, "so")
                StepperRow("Retardo", fmtDelay(prefs.subDelayMs), { prefs.subDelayMs -= 100 }, { prefs.subDelayMs += 100 }, "sd")
                ChoiceRow("Tamaño", SubSizes, prefs.subSize, "ss") { prefs.subSize = it }
                ChoiceRow("Fondo", SubBgs, prefs.subBg, "sb") { prefs.subBg = it }
                Note("Retardo entre −2 y +2 segundos (positivo = salen más tarde). Desde el reproductor se puede ajustar solo para una película con «Opciones».")
            }

            Section("Reproducción") {
                ChoiceRow("Siguiente episodio automático", listOf("Sí", "No"), if (prefs.autoNext) 0 else 1, "an") { prefs.autoNext = it == 0 }
                ChoiceRow("Formato de canales", listOf("HLS", "TS"), if (prefs.liveFormat == "ts") 1 else 0, "lf") {
                    prefs.liveFormat = if (it == 1) "ts" else "m3u8"
                }
                Note("Si algún canal no arranca, prueba el otro formato (la app ya lo intenta sola si falla).")
                ActionButton("Borrar «Continuar viendo»") { prefs.clearHistory() }
            }

            Section("Contenido y control parental") {
                ActionButton("Ocultar y proteger carpetas  ›") { nav.push(Screen.Folders) }
                when {
                    prefs.pin.isEmpty() -> {
                        Note("Pon un PIN para proteger carpetas (por ejemplo, contenido adulto).")
                        ActionButton("Crear PIN") { pinDialog = "set" }
                    }
                    else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (prefs.unlocked) ActionButton("Volver a bloquear", primary = true) { prefs.unlocked = false }
                        else ActionButton("Desbloquear contenido protegido", primary = true) { pinDialog = "unlock" }
                        ActionButton("Cambiar PIN") { pinDialog = "set" }
                        ActionButton("Quitar PIN") { pinDialog = "remove" }
                    }
                }
            }

            Section("Actualizaciones") {
                Text("Versión instalada: ${BuildConfig.VERSION_NAME}", color = Col.Text, fontSize = 14.sp)
                updStatus?.let { Note(it) }
                updProgress?.let {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(it).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White))
                    }
                }
                val rel = updRelease
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rel == null) {
                        ActionButton("Buscar actualizaciones") {
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
                    } else {
                        ActionButton("Descargar e instalar", primary = true) {
                            if (updProgress != null) return@ActionButton
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
                    }
                }
                Note("Cada versión se comprueba con su huella SHA-256 antes de instalarse.")
            }

            Text("Noir+  ·  ${BuildConfig.VERSION_NAME}", color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Aceptar", primary = true) {
                    err = if (pin.length != 4) "Tienen que ser 4 cifras" else onDone(pin)
                }
                ActionButton("Cancelar") { onCancel() }
            }
        }
    }
}
