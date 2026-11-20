package com.noirplus.tv.ui

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Código de acceso la primera vez que se abre Noir+ en un aparato. En el código solo hay una huella
 * (PBKDF2), nunca el código. Los aparatos que ya tenían una cuenta configurada entran sin pedirlo.
 */
object AccessCode {
    private const val SALT = "yopWWOxpGgIFO9Fz8ZTz6Q=="
    private const val HASH = "xrK5d5C0usTKZiP48+pHZ81zjG6qG4viJ74Z5hqE3z4="

    fun check(input: String): Boolean = runCatching {
        val norm = input.uppercase().filter { it.isLetterOrDigit() }
        val spec = PBEKeySpec(norm.toCharArray(), Base64.decode(SALT, Base64.DEFAULT), 60_000, 256)
        val got = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        java.security.MessageDigest.isEqual(got, Base64.decode(HASH, Base64.DEFAULT))
    }.getOrDefault(false)
}

@Composable
fun AccessGate(onOk: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Col.Bg), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Logo(40)
            Spacer(Modifier.height(10.dp))
            Text("Introduce el código de acceso", color = Col.Text, fontSize = 18.sp, textAlign = TextAlign.Center)
            Text("Noir+ es una app privada. Solo se pide la primera vez en cada aparato.", color = Col.Sub, fontSize = 13.sp,
                textAlign = TextAlign.Center)
            Field("Código", code, KeyboardType.Ascii) { code = it; error = null }
            error?.let { Text(it, color = Col.Sub, fontSize = 13.sp) }
            ActionButton(if (busy) "Comprobando…" else "Entrar", primary = true, key = "access-ok") {
                if (busy) return@ActionButton
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { AccessCode.check(code) }
                    busy = false
                    if (ok) onOk() else error = "Código incorrecto"
                }
            }
        }
    }
}
