package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.M3uSource
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.XtreamSource
import com.noirplus.tv.data.normalizeServer
import com.noirplus.tv.data.xtreamFromM3u
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SetupScreen() {
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(if (prefs.type == "m3u") "m3u" else "xtream") }
    var server by rememberSaveable { mutableStateOf(prefs.server) }
    var user by rememberSaveable { mutableStateOf(prefs.user) }
    var pass by rememberSaveable { mutableStateOf(prefs.pass) }
    var m3u by rememberSaveable { mutableStateOf(prefs.m3u) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstFr = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFr.requestFocus() }
    }

    fun connect() {
        if (loading) return
        error = null
        loading = true
        scope.launch {
            try {
                if (mode == "xtream") {
                    require(server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) { "Rellena servidor, usuario y contraseña" }
                    XtreamSource(server, user.trim(), pass.trim()).login()
                    prefs.saveXtream(normalizeServer(server), user.trim(), pass.trim())
                } else {
                    require(m3u.isNotBlank()) { "Escribe la URL de tu lista M3U" }
                    val x = xtreamFromM3u(m3u)
                    if (x != null) {
                        XtreamSource(x.first, x.second, x.third).login()
                        prefs.saveXtream(x.first, x.second, x.third)
                    } else {
                        M3uSource(m3u.trim()).categories(Kind.LIVE)
                        prefs.saveM3u(m3u.trim())
                    }
                }
                Repo.rebuild()
                nav.reset(Screen.Main)
            } catch (t: Throwable) {
                error = t.message ?: "No se ha podido conectar"
            } finally {
                loading = false
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Col.Accent.copy(alpha = 0.35f), Col.Bg)))
                .padding(56.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Logo(40)
            Spacer(Modifier.height(20.dp))
            Text("Tu contenido, en tu tele.", color = Col.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Introduce los datos que te ha dado tu proveedor. Se guardan solo en esta TV.\n\n" +
                    "Xtream Codes: servidor (con puerto), usuario y contraseña.\n" +
                    "Lista M3U: pega la URL completa (http://...).",
                color = Col.Sub, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Xtream Codes", primary = mode == "xtream", focusRequester = firstFr) { mode = "xtream" }
                ActionButton("Lista M3U", primary = mode == "m3u") { mode = "m3u" }
            }
            if (mode == "xtream") {
                Field("Servidor (ej. http://midominio.com:8080)", server, KeyboardType.Uri) { server = it }
                Field("Usuario", user, KeyboardType.Text) { user = it }
                Field("Contraseña", pass, KeyboardType.Password, password = true) { pass = it }
            } else {
                Field("URL de la lista M3U", m3u, KeyboardType.Uri) { m3u = it }
            }
            error?.let { Text(it, color = Col.Error, fontSize = 14.sp) }
            ActionButton(if (loading) "Conectando…" else "Conectar", primary = true) { connect() }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    type: KeyboardType,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Col.Accent,
            unfocusedBorderColor = Col.Sub,
            focusedTextColor = Col.Text,
            unfocusedTextColor = Col.Text,
            focusedLabelColor = Col.Accent,
            unfocusedLabelColor = Col.Sub,
            cursorColor = Col.Accent,
        ),
    )
}
