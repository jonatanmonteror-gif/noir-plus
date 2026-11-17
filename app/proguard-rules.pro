# Noir+ — reglas de R8
# Media3, OkHttp, Coil y Compose ya traen sus propias reglas.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
# El renderizador de subtítulos con retardo delega en la interfaz Renderer de Media3
-keep class com.noirplus.tv.ui.DelayedTextRenderer { *; }
-keep class com.noirplus.tv.ui.NoirRenderersFactory { *; }
