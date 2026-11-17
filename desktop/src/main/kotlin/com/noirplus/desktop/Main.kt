package com.noirplus.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.noirplus.desktop.data.AppDirs
import com.noirplus.desktop.data.Http
import com.noirplus.desktop.data.Repo
import com.noirplus.desktop.ui.App
import com.noirplus.desktop.ui.KeyBus
import com.noirplus.desktop.ui.NoirTheme
import okio.Path.Companion.toOkioPath
import java.io.File

/** Estado de la ventana, accesible desde el reproductor para la pantalla completa. */
val LocalWindowState = staticCompositionLocalOf<WindowState> { error("sin ventana") }

fun main() {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "DIRECT3D")
    Repo.rebuild()
    application {
        val state = rememberWindowState(placement = WindowPlacement.Maximized, size = androidx.compose.ui.unit.DpSize(1440.dp, 900.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Noir+",
            icon = painterResource("icon.png"),
            onPreviewKeyEvent = { KeyBus.dispatch(it) },
        ) {
            window.minimumSize = java.awt.Dimension(1000, 640)
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
                    .memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.25).build() }
                    .diskCache {
                        DiskCache.Builder().directory(File(AppDirs.root, "img").toOkioPath())
                            .maxSizeBytes(300L * 1024 * 1024).build()
                    }
                    .crossfade(true)
                    .build()
            }
            CompositionLocalProvider(LocalWindowState provides state) {
                NoirTheme { App() }
            }
        }
    }
}
