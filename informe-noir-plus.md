# Noir+ — Informe completo del proyecto

*Informe a 19/11/2026. Recoge todo lo hecho en la app (16–19 nov 2026) para reutilizarlo en el proyecto **Noir Books**.*

---

## 1. Qué es Noir+

- **App IPTV propia** (listas Xtream Codes y M3U) para ver películas, series y canales en directo de un proveedor.
- **Tres plataformas, misma experiencia:**
  - **Android TV** (tele PEAQ, se instala con Downloader).
  - **Móvil Android.**
  - **Windows** (programa propio, no emulador).
- **Primer nombre provisional:** Nexo TV. Pasó a **Noir+** el 17/11.
- **Versiones a 19/11:** Android **v1.0.92**, Windows **1.0.52**.
- **Repositorio:** https://github.com/jonatanmonteror-gif/noir-plus (público).
- **Descarga directa del APK:** https://github.com/jonatanmonteror-gif/noir-plus/releases/latest/download/NoirPlus.apk
- **Código de Downloader:** **1220987** (aftv.news/1220987). Siempre apunta a la última versión.
- **Instalador de Windows:** https://github.com/jonatanmonteror-gif/noir-plus/releases/download/windows/NoirPlus-Setup.exe

---

## 2. Identidad visual y criterios de diseño

- **Nombre y logo:** «Noir» con un «+» en superíndice fino, plateado.
- **Estilo:** Apple TV / Netflix, **monocromo** (negro, blancos y grises), sin colores llamativos.
- **Tipografías:**
  - **Inter** para el texto.
  - **Inter Display** para los títulos.
  - **Playfair Display** para el saludo de bienvenida.
  - Subtítulos en Windows: Inter, Atkinson Hyperlegible y Verdana.
- **Idioma:** toda la interfaz en español.
- **Tele:**
  - El foco se marca con un **borde blanco**, sin crecer, sin sombra y sin resplandor, para que las filas no den saltos.
  - Animaciones mínimas.
  - Carátulas grandes.
  - Las filas solo se deslizan al llegar al borde.
- **Móvil:**
  - Estilo Netflix, con barra inferior de iconos en todas las pantallas (inicio, fichas, «Ver todo», carpetas).
  - Pantalla completa sin barras del sistema.
  - Menús laterales a la derecha.
  - En horizontal mantiene el diseño de móvil, no el de tableta.
- **Avisos flotantes** (capítulo nuevo, «Ya disponible»):
  - Tarjeta casi negra con **borde blanco fino**.
  - Esquinas muy redondeadas.
  - **20 de margen** por arriba y por los lados.
- **Preferencias de Jonatan aprendidas por el camino:**
  - No le gustan las pastillas de estado confusas («Ya disponible», «En tu lista»). Prefiere **botones de acción claros**: «Ver ahora», «Avísame», «Tráiler».
  - Nada de cajas apelotonadas. En el móvil, todo a lo ancho y con títulos de sección.
  - Botones uno al lado del otro cuando caben. Si no caben, el secundario va solo con icono.
  - Etiquetas cortas y directas («Avísame», «Nueva serie»).
  - Cada aparato con su propio diseño, sin forzar el mismo en todos.

---

## 3. Arquitectura técnica

### Android (tele + móvil, un solo APK)
- **Lenguaje y librerías:**
  - Kotlin y **Jetpack Compose** (BOM 2024.09).
  - **Media3 ExoPlayer 1.4.1** con HLS.
  - **Coil** para imágenes.
  - **OkHttp** para la red.
  - minSdk 21, targetSdk 35.
- **Un mismo código detecta el aparato** (`Dims.kt`: TvDims, PhoneDims, TabletDims):
  - La tele se escala al 85 %.
  - El móvil se decide por `smallestScreenWidthDp < 600`, para que en horizontal siga siendo móvil.
- **Navegación propia:** pila `Nav` con `SaveableStateHolder`.
  - Al volver atrás se conservan la posición, la pestaña y los filtros.
  - `Memo.kt` guarda en memoria lo último que cargó cada pantalla, que reaparece al momento.
- **Ficheros principales:**
  - **Datos:** `Source.kt` (Xtream/M3U), `Repo.kt`, `Prefs.kt`, `Library.kt`, `LiveIndex.kt` (canales), `MovistarDial.kt`, `Tmdb.kt`, `Platforms.kt`, `Upcoming.kt`, `Updater.kt`, `Sync.kt`/`SyncCore.kt`, `ErrorLog.kt`.
  - **Pantallas:** `MainScreen`, `CategoryScreen`, `DetailScreen`, `PlayerScreen`, `LiveScreen`, `UpcomingScreen`, `SettingsScreen`, `WelcomeScreen`, `SetupScreen`, `FoldersScreen`, `ItemMenu` (menú de pulsación larga), `WhatsNew`, `ResumePoint`, `Memo`.
- **Estabilidad de compilación:** sin R8 ni recorte de recursos. Se probaron y se quitaron porque daban problemas.

### Windows
- Kotlin + **Compose Desktop**, reproductor con **VLC (vlcj)**. Necesita **VLC de 64 bits** instalado.
- **Pintado del vídeo:** `CallbackVideoSurface` a un Bitmap de Skia, bajo el mismo cerrojo que usa VLC. Así se arregló el cierre «Failed to Image::makeFromBitmap».
- **Datos en `%APPDATA%\NoirPlus`:** `settings.json`, `errors.log`, listas, caché de imágenes, datos de IMDb y de TMDB.
- **Ficheros que comparte con Android** (adaptados): `LiveIndex`, `MovistarDial`, `Upcoming`, `Tmdb`, `SyncCore`.

### Automatización (GitHub Actions)
- **`build.yml`:** compila el APK y publica `v1.0.N` con su huella SHA-256 y sus novedades.
- **`windows.yml`:**
  - Compila el instalador `.exe`.
  - **Prueba que el programa arranca** (lo abre 40 s en un Windows limpio). Si no abre, no se publica.
  - Publica `windows-v1.0.N` y actualiza la versión fija `windows`, que es la que usa el actualizador.
- **`catalog.yml`** (diario), sin pedir nada al proveedor del usuario:
  - `build_catalog.py`: catálogos de cada plataforma en España (TMDB), uno pequeño por plataforma, en `cat-*.tsv.gz`.
  - `build_upcoming.py`: próximos estrenos en `upcoming.json.gz`.
  - Logos de las plataformas.
- **`changelog.py`:** convierte `changelog/android.txt` y `changelog/windows.txt` en `changelog.json` para cada versión.
- **Las acciones de GitHub van fijadas a un commit concreto** (seguridad).

### Seguridad y privacidad
- **El servidor y las credenciales del proveedor nunca van en el código** (repositorio público). Solo se guardan en cada aparato.
- **Registro de errores** sin credenciales.
- **Cada APK e instalador va con su huella SHA-256.**
- **Clave de firma nueva** cifrada en el repo (`noirplus.jks.enc`). Pendiente: Jonatan pondrá el secreto `NOIR_KEY_PASSWORD` más adelante, y al activarla habrá que reinstalar una vez.
- **Clave de TMDB:** va en la app. Es de uso gratuito no comercial y la atribución está en Ajustes.

---

## 4. Funciones, por áreas

### 4.1 Entrada, cuenta y bienvenida
- **Configuración inicial:**
  - Datos de la cuenta Xtream o enlace M3U.
  - En la tele, se pueden meter **por QR** desde el móvil.
  - Si https falla por TLS, la app reintenta con http, con mensajes de error claros.
- **Bienvenida:**
  - Saludo en Playfair Display.
  - Botones «Entrar» o «Actualizar listas», con progreso.
  - Novedades desde la última visita.
  - Fondo de carátulas más o menos atenuado según el aparato.
- **Perfil y nombre editables** (en la tele, con el mando).

### 4.2 Inicio, Películas y Series
- **Inicio:**
  - Carrusel de destacados con fundido entre portadas.
  - Continuar viendo, Mi lista y filas por secciones.
- **Filas:** **20 títulos fijos** + «Ver todo +N».
- **Vistas de Películas y Series:**
  - **Plataformas** (Netflix, Max, Disney+, Prime, Apple TV+, Movistar+, Filmin, SkyShowtime…) y **Géneros**.
  - En el móvil hay además **Recién subido**. En la tele, Recién subido está en Ajustes y las carpetas del proveedor están ocultas.
  - «Ver todas las películas/series ›» discreto a la derecha.
- **Al volver de una ficha** se mantiene la pestaña. La tele siempre entra en Plataformas.
- **Filtros combinables:** varias plataformas y varios géneros a la vez. Mezclar plataformas y géneros da los títulos que cumplen las dos cosas.
- **Plataformas deducidas** cruzando la lista del proveedor con los catálogos reales de TMDB en España (preparados a diario, ligeros, leídos línea a línea para no agotar la memoria de la tele).
- **Orden** (solo desde Ajustes):
  - **«Últimos estrenos»** por defecto: las películas por su fecha de estreno.
  - Las series por su **último capítulo ya disponible en el proveedor** (fecha de TMDB cruzada con la de actualización del proveedor). Una temporada 2 nueva sube arriba aunque la serie sea de 2024.
  - También por valoración o el orden del proveedor. El A–Z se quitó.
- **Columnas de la cuadrícula** elegibles. Buscador y orden en cada «Ver todo».
- **Carpetas del proveedor:** ocultables, o protegidas con **PIN** con una etiqueta «+18». Pantalla rediseñada con un selector «Visible / Oculta / Con PIN».

### 4.3 Ficha de película o serie
- **Fecha de estreno** en vez de la duración, sacada de TMDB, del proveedor o, si no hay otra, del año.
- **Películas:** «Estreno en cines: X · En Netflix: Y» (fechas de TMDB para España).
- **Series:** «Último capítulo» disponible.
- **Tráiler en español** (de España o, si no hay, cualquiera en español), en YouTube.
- **Mi lista, Valorar y temporadas.** Los episodios aparecen en lista en el móvil.
- **Nota de IMDb** (solo Windows): datos públicos de IMDb descargados una vez por semana y cruzados por el código de IMDb de TMDB. El origen se explica en Ajustes → Valoraciones. FilmAffinity se descartó porque no tiene API.

### 4.4 Reproductor (Android)
- **Subtítulos propios:**
  - Posición y altura fina.
  - Tamaño de 14 a 26 y 5 tipografías.
  - Caja semitransparente.
  - Retardo de ±2 s.
- **Ruedita de opciones** con secciones plegables:
  - Audio, Subtítulos, Opciones, Velocidad (pasos de 0,1), Formato y Calidad (en directo).
  - Cada opción va como botón rápido o dentro de la ruedita, nunca en los dos sitios.
- **Mando de la tele:**
  - **OK** pausa y reanuda. **↑** abre Audio. **↓** abre los controles.
  - **← / →** retroceden y avanzan **10 s**. Si se vuelve a pulsar seguido, **20, 50 y 100 s**, y se ve en pantalla cuánto («» +50 s»).
  - La barra de progreso no se puede seleccionar con el mando.
  - En directo, **↑ / ↓ cambian de canal**.
- **Móvil:**
  - Gestos de brillo (lado izquierdo) y volumen (derecho).
  - Gira a horizontal al reproducir.
- **Siguiente episodio automático** con cuenta atrás.
- **Progreso guardado** cada 15 s y al salir. Pasado el 95 %, se da por visto.
- **Errores:**
  - Mensaje claro según el código HTTP: cuenta (401/403), contenido retirado (404), máximo de conexiones (429/458/509), servidor caído (5xx).
  - **Un solo reintento** a los 3 s.
  - En directo, prueba TS ↔ HLS y después la siguiente versión del canal.

### 4.5 Directo (canales)
- **Temáticas en vez de las carpetas del proveedor:** ★ Favoritos, **Movistar+**, Deportes, Cine y series, Documentales, Infantil, Noticias, Generalistas, Música, Otros y Adultos.
- **Clasificación automática** (`LiveIndex.kt`, mismo código en Android y Windows):
  - Por nombre del canal y, si es claro, por la carpeta del proveedor.
  - Afinada con la lista real (971 entradas → 657 canales): «M.» y «M+» de Movistar, nombres pegados, regionales…
  - Los «18» de DAZN Eventos no cuentan como adultos. Liga de Campeones y Federación van a Deportes. El canal 24 horas, a Noticias.
- **Todas las versiones de cada canal** (HD, FHD, 4K, bajas conexiones…) salen **seguidas y por separado**, por si alguna no funciona.
- **Carpeta Movistar+:**
  - Los canales están ordenados y **numerados como el dial de Movistar Plus+ 2026** (La 1 = 1… M+ LaLiga = 54, DAZN F1 = 69, Teledeporte = 79, Clan = 118, 24h = 127…). En el 9 va TV3.
  - De cada canal se usa la **versión que arranca más rápido**. Se mide en segundo plano, de una en una, como mucho 60 por vez y en orden de dial. La medición se repite cada semana; si una falla, se vuelve a probar a la hora. **Se pausa mientras se reproduce algo**, para no abrir una segunda conexión.
  - **«Cambiar por…»** (pulsación larga, o clic derecho en Windows) para elegir otra versión. La elección se guarda.
  - **Directo siempre se abre en Movistar+.**
- **Vista:** **lista** por defecto, o cajas con un icono discreto. Filtro de país en pastillas (España primero).
- **Pulsación larga** (clic derecho en Windows): Favoritos y Cambiar temática (se recuerda).
- **Móvil:** deslizar a los lados cambia de temática.

### 4.6 Próximos estrenos
- **Datos:** lista diaria preparada en GitHub desde TMDB, con series nuevas, temporadas, capítulos y películas (solo estrenos en **plataformas**, los de cines no).
- **Pestañas:**
  - **«Próximamente»** (por defecto): el **próximo mes**.
  - **«Recién estrenados»**: la **última semana**, lo más reciente primero.
  - Además, Películas y Series nuevas, y filtro de plataforma.
- **Capítulos:** solo los de series que sigues (en Mi lista o en Continuar viendo).
- **Filtro de ruido** por idioma y popularidad.
- **Tarjeta:**
  - Portada vertical completa.
  - **Logo de la plataforma** arriba a la derecha.
  - Título en dos líneas, tipo en mayúsculas (ESTRENO EN PRIME VIDEO, NUEVA SERIE, TEMPORADA 2…) y géneros justo debajo.
  - Botones **«Ver ahora» / «Avísame» / «Te avisaré»** + **«Tráiler»**.
- **Diseño por aparato:**
  - **Tele:** dos columnas, con la fecha al lateral.
  - **Móvil:** a todo el ancho, con la fecha como título encima de cada día y el tráiler solo con icono.
  - **Windows:** 2 o 3 columnas (se elige en Ajustes y se guarda), con bloques por día.
- **Avisos:** «Ya está disponible» cuando el proveedor sube algo que pediste, y **«Nuevo capítulo disponible»** de las series que sigues.
- **La lista de estrenos** se renueva cada 6 horas.

### 4.7 Continuar viendo y Mi lista
- **Pulsación larga** (clic derecho en Windows): **Volver a empezar** y **Quitar de Continuar viendo**.
- **Arreglo en la tele:** al soltar la pulsación larga ya no se activaba la primera opción ni se abría la película.
- **Mi lista** agrupa películas, series y canales.

### 4.8 Listas y actualizaciones de contenido
- **Las listas se guardan en el aparato** y **no se descargan al abrir**, para no cargar al proveedor. Esto evitó un posible bloqueo por demasiadas descargas.
- **Se renuevan solo:**
  - A las **8, 14, 20 y 2 h** si la app está abierta en ese momento.
  - Con **«Actualizar listas»**, que dice cuántas películas, series y canales nuevos ha encontrado.
- **Si la descarga falla**, se sigue con la lista anterior.

### 4.9 Actualizaciones de la app
- **Android:**
  - Comprueba por la web de GitHub, sin el límite de 60 consultas por hora.
  - Descarga sola al abrir y deja «Instalar» seleccionado.
  - El aviso se puede cerrar con ✕ o «Luego».
- **Windows:** el actualizador lee la versión fija `windows`.
- **Novedades por versión:**
  - Salen al actualizar y en Ajustes → Actualizaciones → Novedades.
  - **Cada aparato ve solo lo suyo** (las líneas «Tele:» o «Móvil:» se filtran y se les quita la etiqueta).
  - Windows tiene las suyas, aparte.
- **Avisos de la app:** la ventana de novedades se cierra con ✕, con «Entendido» o tocando fuera.

### 4.10 Sincronización entre aparatos (hecha, sin desplegar)
- **Servidor propio:** `tools/sync-server/` (`sync.php` + `.htaccess`), pensado para subirlo a `public_html/noir-sync/` en Hostinger.
- **Cuenta:**
  - Identificada por una huella del servidor y el usuario.
  - La clave secreta es una huella que incluye la contraseña, que nunca se envía en claro.
- **Qué sincroniza:**
  - Continuar viendo, Mi lista, Valoraciones y Ajustes.
  - Windows: solo Continuar viendo y Mi lista.
  - Si un mismo elemento cambia en dos aparatos, gana el cambio más reciente.
- **Lista de aparatos** con Incluir y Excluir.
- **Pendiente:** subirlo (falta el dominio) y escribir el dominio en cada aparato.

### 4.11 Ajustes
- **Secciones plegables:**
  - Perfil, cuenta y sincronización.
  - Recién subido (en la tele) y actualizar listas.
  - Plataformas (con la atribución a TMDB) y valoraciones (ocultas por defecto en la tele).
  - Reproductor, subtítulos y reproducción.
  - Control parental con PIN y registro de errores.
  - Actualizaciones, con su SHA-256.
- **Solo en Windows:**
  - Vista inicial de Películas y Series.
  - Columnas de Próximos.
  - Modos de pantalla (completa, inmersiva, ventana).
  - Estilo de subtítulos (XXS–XL, posición y fondo).
  - Mini-reproductor en una esquina mientras navegas.

### 4.12 Estabilidad (lo que se ha blindado)
- **Aviso al abrir** con el informe si la app se cerró sola.
- **Listas a prueba de elementos repetidos**, porque las claves duplicadas cerraban la app.
- **Cargas de pantalla a prueba de fallos:** si una falla, se queda lo que había.
- **Al volver de un tráiler**, aunque la tele haya cerrado la app, se vuelve a la misma ficha y pestaña (`ResumePoint`).
- **La actividad no se reinicia** por cambios de pantalla de la tele (`configChanges` ampliados).
- **Medición de canales:** se pausa al reproducir, va en lotes pequeños y con acceso seguro entre hilos.
- **Windows:** prueba de arranque obligatoria antes de publicar.
- **Revisión externa con Gemini:** el reproductor de Android **no mostró fallos reales**. El resto se quedó sin revisar porque las consultas tardaban más de lo que aguanta la conexión.

---

## 5. Errores importantes y cómo se resolvieron (lecciones)

| Problema | Causa | Solución |
|---|---|---|
| «No se pudo comprobar» al buscar actualización | Límite de 60 consultas/hora de la API de GitHub | Leer la redirección `releases/latest` (Android) y una versión fija `windows` |
| La tele se quedaba sin memoria con las plataformas | Catálogos grandes cargados de golpe | Un archivo pequeño por plataforma, preparado a diario y leído línea a línea |
| Cierre en Windows «Image::makeFromBitmap» | Pintar el vídeo mientras VLC lo escribía | Pintar bajo el mismo cerrojo |
| Nombres de pistas «EspaÃ±ol» | Codificación mal leída | Limpieza de nombres de pistas |
| ERROR 404 «ya no tiene este contenido» | El proveedor estaba resubiendo contenido | Se probó buscar otra versión automáticamente, **cerraba la app en la tele** y se **revirtió**. Lección: no automatizar recuperaciones complejas en el reproductor |
| Windows no abría | Instalación a medias | Reinstalar encima. Desde entonces, prueba de arranque en CI |
| Reproducción fallando en tele y PC (el móvil con datos sí iba) | Posible freno del proveedor por demasiadas descargas de listas | Listas guardadas en el aparato y renovación solo a horas fijas |
| La tele se cerraba sola | Listas con claves repetidas | Claves con el índice incluido |
| Al soltar la pulsación larga se abría la película | La tecla soltada llegaba al menú | Ignorar el soltar de la pulsación que abre el menú |
| Al volver atrás la lista saltaba al principio | Las pantallas se recargaban vacías | Memoria de pantallas (`Memo`) y estados guardables |
| Próximos sin lo de la semana pasada | La app guardaba la lista de estrenos 12 h | Caché nueva de 6 h |
| Aviso de actualización imposible de cerrar en el móvil | El botón se salía de la pantalla | Tarjeta a lo ancho con ✕ y botones debajo |
| Franja negra en el móvil en horizontal | Pasaba al diseño de tableta | Decidir por `smallestScreenWidthDp` |

---

## 6. Forma de trabajar que ha funcionado (aplicable a Noir Books)

1. **Mismo código para tele, móvil y tableta** con dimensiones por aparato (`Dims`): un APK y decisiones de diseño por tamaño de pantalla.
2. **CI desde el primer día:**
   - Cada cambio compila y publica versión con número correlativo, huella SHA-256 y novedades.
   - Prueba de arranque antes de publicar.
3. **Novedades por versión** escritas en `changelog/*.txt` y mostradas en la app tras actualizar, filtradas por aparato.
4. **Actualizador propio** sin depender de la API limitada de GitHub. Descarga directa por enlace fijo y código de Downloader.
5. **Nada de secretos ni datos personales en el repositorio público.**
6. **Datos pesados preparados fuera** (GitHub Actions a diario) y servidos como archivos pequeños, para no cargar el aparato ni servidores ajenos.
7. **Todo guardado en el aparato** y renovado a horas fijas o a mano. Si algo falla, se mantiene lo anterior.
8. **Estabilidad antes que automatismos:**
   - Ante un fallo, mensaje claro y como mucho un reintento.
   - Lo que se automatice debe poder desactivarse o revertirse.
9. **Volver atrás sin perder el sitio** (memoria de pantallas y estados guardables) y vuelta al mismo sitio tras salir a otra app.
10. **Registro de errores sin datos personales** y aviso con informe si la app se cerró.
11. **Iterar el diseño con capturas reales** del aparato. Cambios pequeños y publicados al momento.
12. **Ajustes en secciones plegables** y preferencias que se guardan en cada aparato (vistas, columnas, filtros).

---

## 7. Pendiente o en el aire

- Desplegar el servidor de sincronización en Hostinger: falta el dominio.
- Activar la clave de firma nueva (secreto `NOIR_KEY_PASSWORD`). Implica reinstalar una vez.
- Dial 10 de Movistar+ (autonómico): falta decidir qué canal va.
- Guía de programación (EPG) del directo.
- «Enviar a la tele» por DLNA (Samsung, LG) desde el móvil: propuesto, sin empezar.
- Contenido propio (anime) con Jellyfin como segunda fuente: propuesto.
- Versión para LG webOS: sin decidir (alternativa: stick Google TV / Fire TV).
- Revisión con Gemini del resto de módulos: se quedó a medias por los tiempos de la conexión.
- Separar el repositorio de código (privado) del de APK (público).
