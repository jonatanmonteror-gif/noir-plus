package com.noirplus.tv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * Servidor web mínimo en la TV (solo red local) para escribir los datos desde el móvil.
 * El QR lleva un código aleatorio en la dirección: sin él no se acepta nada.
 * Se apaga al salir de la pantalla de inicio de sesión.
 */
class PairServer(private val onSubmit: suspend (Map<String, String>) -> String?) {
    val token: String = run {
        val a = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        (1..10).map { a[r.nextInt(a.length)] }.joinToString("")
    }
    private var socket: ServerSocket? = null
    private var job: Job? = null

    /** Arranca y devuelve la dirección para el QR, o null si no hay red local. */
    fun start(scope: CoroutineScope): String? {
        val ip = localIp() ?: return null
        val ss = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        socket = ss
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                launch { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
        return "http://$ip:${ss.localPort}/$token"
    }

    fun stop() {
        runCatching { socket?.close() }
        job?.cancel()
    }

    private suspend fun handle(c: Socket) {
        c.soTimeout = 15_000
        val input = BufferedInputStream(c.getInputStream())
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0 || head.length > 8192) return
            head.append(b.toChar())
        }
        val lines = head.lines()
        val (method, path) = lines.first().split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val len = lines.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val out = c.getOutputStream()
        fun send(code: String, html: String) {
            val body = html.toByteArray(Charsets.UTF_8)
            out.write(("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
            out.write(body)
            out.flush()
        }
        if (path.trimEnd('/') != "/$token") {
            send("404 Not Found", page("Código no válido", "<p>Vuelve a escanear el QR de la tele.</p>"))
            return
        }
        if (method == "POST") {
            if (len > 16_384) return
            val buf = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(buf, read, len - read)
                if (n < 0) break
                read += n
            }
            val form = String(buf, 0, read, Charsets.UTF_8).split("&").mapNotNull {
                val k = it.substringBefore('=', "")
                if (k.isEmpty()) null else URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }.toMap()
            val err = onSubmit(form)
            if (err == null) send("200 OK", page("¡Listo!", "<p>Ya puedes mirar la tele.</p>"))
            else send("200 OK", page("No se pudo conectar", "<p class=e>${esc(err)}</p><p><a href='/$token'>Volver a intentarlo</a></p>"))
            return
        }
        send("200 OK", formPage())
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun page(title: String, body: String) = """<!doctype html><html lang=es><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>Noir+</title>$CSS
<body><main><h1>Noir<sup>+</sup></h1><h2>${esc(title)}</h2>$body</main></body></html>"""

    private fun formPage() = page("Conectar la tele", """
<form method=post>
<label>Tu nombre<input name=name autocomplete=given-name></label>
<label>Servidor<input name=server placeholder="http://dominio.com:8080" inputmode=url autocapitalize=off></label>
<label>Usuario<input name=user autocapitalize=off autocomplete=off></label>
<label>Contraseña<input name=pass type=password autocomplete=off></label>
<p class=o>o, si tienes una lista M3U:</p>
<label>URL de la lista M3U<input name=m3u inputmode=url autocapitalize=off placeholder="http://…"></label>
<button>Enviar a la tele</button>
</form>
<p class=s>Los datos van directamente a tu tele por tu red local. No pasan por internet.</p>""")

    companion object {
        private const val CSS = """<style>
*{box-sizing:border-box}body{margin:0;background:#000;color:#fff;font:15px -apple-system,system-ui,Inter,Roboto,sans-serif}
main{max-width:420px;margin:0 auto;padding:28px 20px}h1{font-size:26px;margin:0 0 4px;letter-spacing:-.5px}
h1 sup{font-weight:300;color:#bbb;font-size:15px}h2{font-size:18px;font-weight:600;margin:6px 0 20px;color:#ddd}
label{display:block;margin:0 0 14px;color:#8e8e93;font-size:13px}input{display:block;width:100%;margin-top:6px;padding:12px 14px;
border-radius:10px;border:1px solid #2c2c2e;background:#1c1c1e;color:#fff;font-size:16px}input:focus{outline:none;border-color:#fff}
button{width:100%;padding:14px;border:0;border-radius:999px;background:#fff;color:#000;font-size:16px;font-weight:600;margin-top:8px}
.o,.s{color:#8e8e93;font-size:13px}.e{color:#ff6961}a{color:#fff}
</style>"""

        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
