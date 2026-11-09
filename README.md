# TMCVIDEOS — Android V1.0 (prototipo funcional)

Aplicación Android nativa con Kotlin, Jetpack Compose y Media3 ExoPlayer.

## Funciones incluidas
- Inicio estilo neón con categorías y accesos rápidos.
- Lista de TV en vivo importada desde archivos M3U/M3U8 o URL HTTPS.
- Reproductor HLS / archivos MP4 compatibles con Media3.
- Reproducción de archivos de video seleccionados del teléfono.
- Exportación M3U, favoritos e historial locales.
- Ajustes y navegación inferior.

## Generar APK en GitHub
1. Extrae el ZIP y sube **el contenido de la carpeta** a la raíz de tu repositorio `hdca172/TMCVIDEOS` (incluyendo `.github`).
2. Abre **Actions → Compilar APK Android**.
3. Ejecuta **Run workflow** o haz un commit en `main`.
4. Si la compilación termina en verde, abre la ejecución y descarga **TMCVIDEOS-APK-debug** en Artifacts.
5. Descomprime el ZIP descargado e instala `app-debug.apk` en Android.

Este APK de depuración es para pruebas. Una publicación definitiva requiere firma de release con una clave privada protegida en GitHub Secrets, y pruebas reales en dispositivos.

## Limitaciones conocidas
- Es una **primera base funcional**, no una reproducción pixel-perfect de la maqueta ni un lanzamiento definitivo.
- El logo está aproximado con tipografía e iconos; aún falta crear e integrar el logo vectorial original. La imagen de referencia no contiene un archivo de logo separado.
- Se conservan una sola colección de canales y preferencias locales; futuras versiones incorporarán varias listas independientes y EPG.
- La reproducción depende de la disponibilidad, el formato, el cifrado y los permisos de la fuente. No se incluyen canales ni contenidos protegidos de terceros.
- No se ha compilado el APK en este entorno; GitHub Actions será la comprobación real.
