package com.noirplus.tv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.M3uSource
import com.noirplus.tv.data.PairServer
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.friendlyError
import com.noirplus.tv.data.xtreamFromM3u
import com.noirplus.tv.data.xtreamLoginAuto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Comprueba y guarda la cuenta. Devuelve null si todo fue bien o el mensaje de error. */
suspend fun connectAccount(name: String, mode: String, server: String, user: String, pass: String, m3u: String): String? {
    val prefs = Repo.prefs
    return try {
        if (name.isNotBlank()) prefs.name = name
        if (mode == "xtream") {
            require(server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) { "Rellena servidor, usuario y contraseña" }
            val ok = xtreamLoginAuto(server, user.trim(), pass.trim())
            prefs.saveXtream(ok, user.trim(), pass.trim())
        } else {
            require(m3u.isNotBlank()) { "Escribe la URL de tu lista M3U" }
            val x = xtreamFromM3u(m3u)
            if (x != null) {
                val ok = xtreamLoginAuto(x.first, x.second, x.third)
                prefs.saveXtream(ok, x.second, x.third)
            } else {
                M3uSource(m3u.trim()).categories(Kind.LIVE)
                prefs.saveM3u(m3u.trim())
            }
        }
        Repo.rebuild()
        null
    } catch (t: Throwable) {
        friendlyError(t)
    }
}

/** QR en blanco y negro, nítido y pequeño. */
fun qrBitmap(text: String, px: Int = 360): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
    for (x in 0 until m.width) for (y in 0 until m.height) {
        bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp.asImageBitmap()
}

@Composable
fun SetupScreen() {
    val prefs = Repo.prefs
    // App privada: la primera vez en cada aparato se pide el código de acceso
    var accessOk by remember { mutableStateOf(prefs.accessOk) }
    if (!accessOk) {
        AccessGate { prefs.accessOk = true; accessOk = true }
        return
    }
    val nav = LocalNav.current
    val d = LocalDims.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(prefs.name) }
    var mode by rememberSaveable { mutableStateOf(if (prefs.type == "m3u") "m3u" else "xtream") }
    var server by rememberSaveable { mutableStateOf(prefs.server) }
    var user by rememberSaveable { mutableStateOf(prefs.user) }
    var pass by rememberSaveable { mutableStateOf(prefs.pass) }
    var m3u by rememberSaveable { mutableStateOf(prefs.m3u) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFr.requestFocus() }
    }
    LaunchedEffect(done) { if (done) nav.reset(Screen.Main) }

    // TV: servidor local + QR para escribir los datos desde el móvil
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    if (d.tv) {
        DisposableEffect(Unit) {
            val server0 = PairServer { form ->
                val err = connectAccount(
                    name = form["name"].orEmpty(),
                    mode = if (form["m3u"].isNullOrBlank()) "xtream" else "m3u",
                    server = form["server"].orEmpty(),
                    user = form["user"].orEmpty(),
                    pass = form["pass"].orEmpty(),
                    m3u = form["m3u"].orEmpty(),
                )
                if (err == null) withContext(Dispatchers.Main) { done = true }
                err
            }
            val u = server0.start(scope)
            qrUrl = u
            if (u != null) qr = qrBitmap(u)
            onDispose { server0.stop() }
        }
    }

    fun connect() {
        if (loading) return
        if (name.isBlank()) {
            error = "Escribe tu nombre"
            return
        }
        error = null
        loading = true
        scope.launch {
            val err = connectAccount(name, mode, server, user, pass, m3u)
            loading = false
            if (err == null) done = true else error = err
        }
    }

    val intro: @Composable () -> Unit = {
        Logo(if (d.compact) 34 else 40)
        Spacer(Modifier.height(if (d.compact) 14.dp else 20.dp))
        Text("Tu contenido, en tu tele.", color = Col.Text, fontFamily = InterDisplay, fontSize = if (d.compact) 26.sp else 30.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Text(
            "Introduce los datos que te ha dado tu proveedor. Se guardan solo en este dispositivo.",
            color = Col.Sub, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp),
        )
        val bmp = qr
        if (bmp != null) {
            Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                    Image(bmp, "Código QR", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text("¿Mejor desde el móvil?", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Escanea el código con la cámara y escribe los datos allí. Móvil y tele deben estar en la misma wifi.",
                        color = Col.Sub, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp).width(240.dp))
                }
            }
        }
    }
    val form: @Composable () -> Unit = {
        Field("Tu nombre", name, KeyboardType.Text, focusRequester = if (d.tv) null else firstFr) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            ActionButton("Xtream Codes", primary = mode == "xtream", focusRequester = if (d.tv) firstFr else null) { mode = "xtream" }
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

    if (d.compact) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = d.edge + 4.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column { intro() }
            Spacer(Modifier.height(8.dp))
            form()
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Col.Surface, Col.Bg)))
                .padding(d.edge + 8.dp),
            verticalArrangement = Arrangement.Center,
        ) { intro() }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.edge + 8.dp, vertical = d.edge),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { form() }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    type: KeyboardType,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Col.Surface2,
            focusedTextColor = Col.Text,
            unfocusedTextColor = Col.Text,
            focusedLabelColor = Col.Text,
            unfocusedLabelColor = Col.Sub,
            cursorColor = Col.Text,
        ),
    )
}
