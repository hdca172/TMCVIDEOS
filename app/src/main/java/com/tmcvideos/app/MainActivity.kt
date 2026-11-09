package com.tmcvideos.app

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

private val Back = Color(0xFF050712)
private val Surface = Color(0xFF12192D)
private val Gold = Color(0xFFFFCD35)
private val Red = Color(0xFFF10834)
private val Blue = Color(0xFF2675FF)
private val Purple = Color(0xFF9E3DFF)
private val White = Color(0xFFF5F7FF)

private data class Channel(val name: String, val url: String, val group: String, val id: String = UUID.randomUUID().toString())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Back, surface = Surface)) {
            TmcApp(this)
        } }
    }
}

@Composable
private fun TmcApp(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("tmcvideos", 0) }
    var screen by remember { mutableStateOf("Inicio") }
    var channels by remember { mutableStateOf(readChannels(prefs.getString("playlist", "") ?: "")) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("Todos") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf(prefs.getString("source_name", "Mi lista") ?: "Mi lista") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val favorites = remember { mutableStateListOf<String>().also { it.addAll((prefs.getString("favorites", "") ?: "").split("\n").filter(String::isNotBlank)) } }
    val history = remember { mutableStateListOf<String>().also { it.addAll((prefs.getString("history", "") ?: "").split("\n").filter(String::isNotBlank)) } }
    fun savePlaylist(list: List<Channel>) {
        val unique = list.distinctBy { it.url }
        channels = unique
        prefs.edit().putString("playlist", exportM3u(unique)).apply()
    }
    fun play(c: Channel) {
        selected = c
        history.remove(c.url)
        history.add(0, c.url)
        while (history.size > 100) history.removeAt(history.lastIndex)
        prefs.edit().putString("history", history.joinToString("\n")).apply()
        screen = "Reproductor"
    }
    fun toggleFavorite(c: Channel) {
        if (favorites.contains(c.url)) favorites.remove(c.url) else favorites.add(c.url)
        prefs.edit().putString("favorites", favorites.joinToString("\n")).apply()
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val content = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val parsed = readChannels(content)
                if (parsed.isEmpty()) message = "No se encontraron canales compatibles."
                else { savePlaylist(channels + parsed); message = "Importados ${parsed.size} canales" }
            } catch (e: Exception) { message = "No se pudo leer el archivo: ${e.localizedMessage}" }
        }
    }
    val videoFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) play(Channel("Mi video", uri.toString(), "Mis videos"))
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) {
            try { activity.contentResolver.openOutputStream(uri)?.use { it.write(exportM3u(channels).toByteArray()) }; message = "Lista exportada" }
            catch (e: Exception) { message = "Error al exportar" }
        }
    }
    Column(Modifier.fillMaxSize().background(Back)) {
        TmcHeader(screen, onBack = { screen = "Inicio" })
        Box(Modifier.weight(1f)) {
            when (screen) {
                "Inicio" -> LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        HeroCard { screen = "TV en vivo" }
                        Spacer(Modifier.height(12.dp))
                        NeonCard {
                            Row(Modifier.fillMaxWidth().clickable { screen = "Listas M3U" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Red, Purple)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlaylistAdd, "Agregar lista M3U", tint = White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Agregar lista M3U", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text("Importa desde archivo o enlace HTTP/HTTPS", color = Color.LightGray, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Gold)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Accesos rápidos")
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTile("TV en vivo", Color(0xFF6627ED), Icons.Default.Tv, Modifier.weight(1f)) { categoryFilter = "Todos"; searching = ""; screen = "TV en vivo" }
                            QuickTile("Videos", Blue, Icons.Default.PlayCircle, Modifier.weight(1f)) { screen = "Videos" }
                            QuickTile("Mis videos", Color(0xFF0C9B51), Icons.Default.Folder, Modifier.weight(1f)) { videoFile.launch(arrayOf("video/*")) }
                            QuickTile("Favoritos", Color(0xFFFF651A), Icons.Default.Star, Modifier.weight(1f)) { searching = ""; screen = "Favoritos" }
                        }
                        Spacer(Modifier.height(18.dp))
                        SectionTitle("Categorías")
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTile("Deportes", Color(0xFF159445), Icons.Default.SportsSoccer, Modifier.weight(1f)) { categoryFilter = "Deportes"; screen = "TV en vivo" }
                            QuickTile("Noticias", Blue, Icons.Default.Newspaper, Modifier.weight(1f)) { categoryFilter = "Noticias"; screen = "TV en vivo" }
                            QuickTile("Música", Color(0xFF6C20C9), Icons.Default.MusicNote, Modifier.weight(1f)) { categoryFilter = "Música"; screen = "TV en vivo" }
                            QuickTile("Infantil", Color(0xFFDF6906), Icons.Default.ChildCare, Modifier.weight(1f)) { categoryFilter = "Infantil"; screen = "TV en vivo" }
                        }
                        Spacer(Modifier.height(18.dp))
                        NeonCard { Row(Modifier.fillMaxWidth().clickable { searching = ""; screen = "Historial" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = Gold); Spacer(Modifier.width(12.dp)); Text("Historial de reproducción", color = White, modifier = Modifier.weight(1f)); Text("${history.size}", color = Color.LightGray) } }
                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Últimos agregados")
                        if (channels.isEmpty()) EmptyState("Agrega una lista M3U para mostrar tus canales")
                    }
                    items(channels.takeLast(8).reversed()) { ChannelRow(it, favorites.contains(it.url), { play(it) }, { toggleFavorite(it) }) }
                }
                "TV en vivo", "Explorar", "Favoritos", "Historial" -> {
                    val list = when (screen) {
                        "Favoritos" -> channels.filter { it.url in favorites }
                        "Historial" -> history.map { id -> channels.find { it.url == id } ?: Channel(if (id.startsWith("content://")) "Video local" else "Video reproducido", id, "Historial") }
                        else -> channels
                    }
                    val shown = list.filter { c ->
                        (screen != "TV en vivo" || categoryFilter == "Todos" || c.group.contains(categoryFilter, true)) &&
                            (c.name.contains(searching, true) || c.group.contains(searching, true))
                    }
                    Column(Modifier.fillMaxSize().padding(14.dp)) {
                        if (screen == "TV en vivo" || screen == "Explorar") {
                            OutlinedTextField(searching, { searching = it }, label = { Text("Buscar canales") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            if (screen == "TV en vivo") {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (listOf("Todos") + channels.map { it.group }.distinct().sorted()).forEach { group ->
                                        FilterChip(selected = categoryFilter == group, onClick = { categoryFilter = group }, label = { Text(group) })
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        if (shown.isEmpty()) {
                            EmptyState(when (screen) {
                                "Favoritos" -> "Todavía no tienes favoritos. Pulsa el corazón de un canal para guardarlo."
                                "Historial" -> "Aún no has reproducido videos o canales."
                                else -> if (channels.isEmpty()) "No hay canales. Importa una lista M3U." else "No hay resultados. Prueba otra categoría o búsqueda."
                            })
                            if (screen == "TV en vivo") TextButton(onClick = { categoryFilter = "Todos"; searching = "" }) { Text("Mostrar todos los canales") }
                            if (screen == "TV en vivo" && channels.isEmpty()) Button(onClick = { screen = "Listas M3U" }) { Text("Agregar lista M3U") }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shown, key = { it.id }) { c -> ChannelRow(c, favorites.contains(c.url), { play(c) }, { toggleFavorite(c) }) }
                        }
                    }
                }
                "Listas M3U" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Mis listas de reproducción")
                    Text("Añade tus propias listas M3U/M3U8 autorizadas para ver los canales.", color = Color.LightGray, fontSize = 13.sp)
                    NeonCard { Row(Modifier.fillMaxWidth().clickable { importFile.launch("*/*") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FileOpen, null, tint = Blue); Spacer(Modifier.width(12.dp)); Text("Importar archivo .m3u / .m3u8", color = White) } }
                    OutlinedTextField(urlInput, { urlInput = it }, label = { Text("URL de lista M3U (HTTP o HTTPS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        val candidate = urlInput.trim()
                        if (!isWebUrl(candidate)) { message = "Introduce un enlace HTTP o HTTPS válido"; return@Button }
                        busy = true
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Importando..." else "Importar desde URL") }
                    LaunchedEffect(busy) {
                        if (busy) {
                            try {
                                val content = withContext(Dispatchers.IO) { downloadPlaylist(urlInput.trim()) }
                                val found = readChannels(content)
                                if (found.isEmpty()) message = "No se detectaron canales M3U. Comprueba que la URL devuelve una lista #EXTM3U y no una página web o una transmisión individual."
                                else { savePlaylist(channels + found); message = "Importados ${found.size} canales" }
                            } catch (e: Exception) { message = if (e.message?.contains("HTTP 403") == true) "HTTP 403: el servidor denegó el acceso a la lista. Verifica que el enlace siga autorizado y consulta al proveedor; la aplicación no puede eliminar esa restricción." else "No se pudo descargar la lista: ${e.localizedMessage ?: "error de conexión"}. Revisa la URL y el servidor." }
                            busy = false
                        }
                    }
                    NeonCard { Column(Modifier.padding(14.dp)) { Text("Mi lista", color = White, fontWeight = FontWeight.Bold); Text("${channels.size} canales importados", color = Color.LightGray, fontSize = 12.sp); Text("Las listas se guardan localmente en este dispositivo.", color = Color.Gray, fontSize = 11.sp) } }
                    Button(onClick = { exportFile.launch("TMCVIDEOS.m3u") }, enabled = channels.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Exportar lista M3U") }
                    OutlinedButton(onClick = { showDeleteConfirmation = true }, enabled = channels.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Eliminar canales importados") }
                }
                "Videos" -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Videos")
                    Button(onClick = { videoFile.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Abrir video de mi teléfono") }
                    OutlinedTextField(urlInput, { urlInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("URL HTTP/HTTPS de video o transmisión") }, singleLine = true)
                    Button(onClick = { if (isWebUrl(urlInput.trim())) play(Channel("Video de Internet", urlInput.trim(), "Videos")) else message = "Usa una URL HTTP o HTTPS válida" }, modifier = Modifier.fillMaxWidth()) { Text("Reproducir enlace") }
                    Text("Los archivos elegidos se reproducen localmente. Usa solo enlaces y contenido autorizados.", color = Color.Gray, fontSize = 12.sp)
                }
                "Reproductor" -> selected?.let { c -> PlayerScreen(c) { toggleFavorite(c) } }
                "Ajustes" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Configuración")
                    listOf("Reproductor" to "Media3 ExoPlayer", "Calidad" to "Depende de la fuente", "Tema" to "Oscuro neón", "Idioma" to "Español", "Versión" to "TMCVIDEOS V2.0").forEach { (a,b) -> NeonCard { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(a, color = White); Text(b, color = Color.LightGray, fontSize = 12.sp) } } }
                    Text("Información de la aplicación. Estas opciones todavía no son ajustables.", color = Color.LightGray, fontSize = 12.sp)
                    Button(onClick = { screen = "Listas M3U" }, modifier = Modifier.fillMaxWidth()) { Text("Administrar listas M3U (${channels.size} canales)") }
                    OutlinedButton(onClick = { searching = ""; screen = "Historial" }, modifier = Modifier.fillMaxWidth()) { Text("Ver historial (${history.size})") }
                    OutlinedButton(onClick = { searching = ""; screen = "Favoritos" }, modifier = Modifier.fillMaxWidth()) { Text("Ver favoritos (${favorites.size})") }
                    OutlinedButton(onClick = { history.clear(); prefs.edit().remove("history").apply(); message = "Historial eliminado" }, modifier = Modifier.fillMaxWidth()) { Text("Borrar historial") }
                    OutlinedButton(onClick = { favorites.clear(); prefs.edit().remove("favorites").apply(); message = "Favoritos eliminados" }, modifier = Modifier.fillMaxWidth()) { Text("Borrar favoritos") }
                }
            }
            if (showDeleteConfirmation) AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = { Text("Eliminar canales") }, text = { Text("Se eliminarán los canales importados de este dispositivo. ¿Continuar?") }, confirmButton = { TextButton(onClick = { savePlaylist(emptyList()); showDeleteConfirmation = false; message = "Canales eliminados" }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") } })
            if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("TMCVIDEOS") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("Aceptar") } })
        }
        NavigationBar(containerColor = Color(0xFF090F21)) {
            listOf(Triple("Inicio", Icons.Default.Home, "Inicio"), Triple("TV", Icons.Default.LiveTv, "TV en vivo"), Triple("Listas", Icons.Default.PlaylistAdd, "Listas M3U"), Triple("Favoritos", Icons.Default.FavoriteBorder, "Favoritos"), Triple("Ajustes", Icons.Default.Settings, "Ajustes")).forEach { (label, icon, target) ->
                NavigationBarItem(selected = screen == target, onClick = { if (target == "TV en vivo") { categoryFilter = "Todos"; searching = "" }; if (target == "Favoritos") searching = ""; screen = target }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Red, selectedTextColor = Red, unselectedIconColor = White, unselectedTextColor = White))
            }
        }
    }
}

@Composable private fun TmcHeader(screen: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Back).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(if (screen == "Inicio") Icons.Default.Menu else Icons.Default.ArrowBack, null, tint = White) }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(34.dp).border(1.dp, Gold, RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Red, Purple)), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, "TMCVIDEOS", tint = White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("TMC", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        Text("VIDEOS", color = Red, fontSize = 23.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        Spacer(Modifier.weight(1f))
        Text(if (screen == "Inicio") "" else screen, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun SectionTitle(s: String) { Text(s, color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
@Composable private fun NeonCard(content: @Composable () -> Unit) { Box(Modifier.fillMaxWidth().border(1.dp, Brush.horizontalGradient(listOf(Blue, Purple)), RoundedCornerShape(12.dp)).background(Surface, RoundedCornerShape(12.dp))) { content() } }
@Composable private fun HeroCard(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(160.dp).border(1.dp, Blue, RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF071027), Color(0xFF064EA1), Purple)), RoundedCornerShape(14.dp)).clickable { onClick() }.padding(18.dp)) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("EN VIVO", color = White, modifier = Modifier.background(Red, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("TV EN VIVO", color = White, fontWeight = FontWeight.Black, fontSize = 27.sp)
            Text("Todos tus canales en un solo lugar", color = White, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text("EXPLORAR CANALES  ›", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Icon(Icons.Default.PlayCircle, null, tint = White, modifier = Modifier.align(Alignment.CenterEnd).size(50.dp))
    }
}
@Composable private fun QuickTile(label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(82.dp).background(Brush.verticalGradient(listOf(color, color.copy(alpha = .55f))), RoundedCornerShape(11.dp)).clickable { onClick() }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = White, modifier = Modifier.size(27.dp)); Spacer(Modifier.height(5.dp)); Text(label, color = White, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
    }
}
@Composable private fun ChannelRow(c: Channel, favorite: Boolean, play: () -> Unit, toggle: () -> Unit) {
    NeonCard {
        Row(Modifier.fillMaxWidth().clickable { play() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Blue, Purple)), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Tv, null, tint = White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.group, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = toggle) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Red else White) }
            Icon(Icons.Default.PlayCircleOutline, "Reproducir", tint = White, modifier = Modifier.size(29.dp))
        }
    }
}
@Composable private fun EmptyState(s: String) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.TvOff, null, tint = Purple, modifier = Modifier.size(45.dp)); Spacer(Modifier.height(8.dp)); Text(s, color = Color.LightGray, fontSize = 13.sp) } }

@Composable private fun PlayerScreen(c: Channel, toggle: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playbackError by remember(c.url) { mutableStateOf<String?>(null) }
    val player = remember(c.url) { ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Error de reproducción (${error.errorCodeName}): ${error.cause?.localizedMessage ?: error.localizedMessage ?: "origen o formato incompatible"}"
            }
        })
        setMediaItem(MediaItem.fromUri(Uri.parse(c.url))); prepare(); playWhenReady = true
    } }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(240.dp))
        Column(Modifier.padding(16.dp)) {
            Text(c.name, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp)); Text(c.group, color = Color.LightGray)
            if (playbackError != null) Text(playbackError!!, color = Red, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            NeonCard { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = toggle) { Icon(Icons.Default.FavoriteBorder, "Favorito", tint = Red) }
                IconButton(onClick = { val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, c.url) }; context.startActivity(Intent.createChooser(send, "Compartir enlace")) }) { Icon(Icons.Default.Share, "Compartir", tint = White) }
            } }
            Spacer(Modifier.height(12.dp)); Text("Si un canal no reproduce, comprueba el enlace, la conexión y la compatibilidad del formato.", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

private fun readChannels(m3u: String): List<Channel> {
    val lines = m3u.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val output = mutableListOf<Channel>()
    var name = "Canal"; var group = "General"
    for (line in lines) {
        when {
            line.startsWith("#EXTINF", true) -> {
                name = line.substringAfterLast(',', "Canal").trim().ifBlank { "Canal" }
                group = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1) ?: "General"
            }
            !line.startsWith("#") && (line.startsWith("https://") || line.startsWith("http://")) -> {
                if (output.size < 5000) output.add(Channel(name, line, group))
                name = "Canal"; group = "General"
            }
        }
    }
    return output.distinctBy { it.url }
}
private fun exportM3u(channels: List<Channel>): String = buildString {
    append("#EXTM3U\n")
    channels.forEach { c ->
        append("#EXTINF:-1 group-title=\"").append(c.group.replace("\"", "")).append("\",").append(c.name.replace("\n", " ")).append("\n")
        append(c.url).append("\n")
    }
}

private fun isWebUrl(raw: String): Boolean = try {
    val uri = URI(raw)
    (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
} catch (_: Exception) { false }

private fun downloadPlaylist(raw: String): String {
    require(isWebUrl(raw)) { "URL HTTP/HTTPS inválida" }
    val connection = URL(raw).openConnection().apply {
        connectTimeout = 15000
        readTimeout = 25000
        setRequestProperty("User-Agent", "TMCVIDEOS/2.0 Android")
        setRequestProperty("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*")
    }
    return try {
        if (connection is HttpURLConnection) {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("HTTP $status (${connection.responseMessage})")
        }
        connection.getInputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            val content = reader.readText()
            require(content.length <= 10_000_000) { "La lista supera el límite de 10 MB" }
            content.removePrefix("\uFEFF")
        }
    } finally { (connection as? HttpURLConnection)?.disconnect() }
}
