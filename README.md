# Noir+

Reproductor IPTV para Android TV / Google TV con interfaz tipo Netflix/HBO:
**Inicio · Directo · Películas · Series · Buscar · Ajustes**.

La app no incluye contenido: reproduce la lista que configures (Xtream Codes o M3U).
Las categorías (carpetas) se muestran tal y como vienen de tu proveedor.

## Funciones
- Inicio con cabecera grande, "Continuar viendo", "Mi lista" y filas por categoría.
- Películas y Series: una fila por cada carpeta del proveedor; la cabecera muestra la imagen, nota, año y sinopsis de lo que tengas seleccionado.
- Ficha de película (Reproducir / Continuar / Mi lista) y de serie (temporadas y episodios con progreso).
- Directo: categorías a la izquierda, rejilla de canales, favoritos (mantén OK pulsado) y zapping con ▲ ▼ dentro del reproductor.
- Buscador global (películas, series y canales).
- Reproductor ExoPlayer: HLS, TS, MP4, MKV… Si un canal falla en HLS, prueba TS automáticamente.
- Recuerda por dónde ibas en cada película/episodio.

## Compilar (automático)
Cada vez que se sube código a la rama `main`, GitHub Actions compila el APK y lo publica en **Releases**.
El enlace a la última versión es siempre el mismo:

```
https://github.com/USUARIO/REPO/releases/latest/download/NoirPlus.apk
```

> El repositorio tiene que ser **público** para que la TV pueda descargar el APK.

## Instalar en la TV con Downloader
1. En la TV: Play Store → instala **Downloader** (de AFTVnews).
2. Ajustes de la TV → Apps → Seguridad → **Fuentes desconocidas** → activa Downloader.
3. En el móvil/PC entra en **aftv.news** (acortador de AFTVnews), pega el enlace de arriba y te dará un **código numérico**.
4. En Downloader escribe ese código → descarga → **Instalar**.
5. Para actualizar, repite el paso 4 con el mismo código (siempre apunta a la última versión).

## Firma y seguridad
- La clave nueva está cifrada en `app/noirplus.jks.enc`; GitHub Actions la descifra con el secreto `NOIR_KEY_PASSWORD`.
  Mientras ese secreto no exista se sigue firmando con la clave antigua (`app/legacy.jks`) para no tener que reinstalar.
- Cada versión publica `NoirPlus.apk.sha256`; el actualizador de la app comprueba la huella antes de instalar.
- Las acciones de GitHub están fijadas a un commit concreto.
