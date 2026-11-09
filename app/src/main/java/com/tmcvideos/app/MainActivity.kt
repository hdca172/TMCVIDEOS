package com.tmcvideos.app

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

private val Back = Color(0xFF050508)
private val Surface = Color(0xFF121217)
private val Gold = Color(0xFFFFCD35)
private val Red = Color(0xFFFF3158)
private val Blue = Color(0xFF447BFF)
private val Purple = Color(0xFF9E3DFF)
private val White = Color(0xFFF5F7FF)

private data class Channel(val name: String, val url: String, val group: String, val id: String = UUID.randomUUID().toString(), val logo: String = "")

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
    var previousScreen by remember { mutableStateOf("TV en vivo") }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var libraryLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        channels = withContext(Dispatchers.Default) { readChannels(prefs.getString("playlist", "") ?: "") }
        libraryLoaded = true
    }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("Todos") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf(prefs.getString("source_name", "Mi lista") ?: "Mi lista") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<Channel?>(null) }
    var editName by remember { mutableStateOf("") }
    var editGroup by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var autoplay by remember { mutableStateOf(prefs.getBoolean("autoplay", true)) }
    var diagnostics by remember { mutableStateOf(prefs.getBoolean("diagnostics", true)) }
    val favorites = remember { mutableStateListOf<String>().also { it.addAll((prefs.getString("favorites", "") ?: "").split("\n").filter(String::isNotBlank)) } }
    val history = remember { mutableStateListOf<String>().also { it.addAll((prefs.getString("history", "") ?: "").split("\n").filter(String::isNotBlank)) } }
    fun savePlaylist(list: List<Channel>) {
        val unique = list.distinctBy { it.url }
        channels = unique
        scope.launch {
            val serialized = withContext(Dispatchers.Default) { exportM3u(unique) }
            withContext(Dispatchers.IO) { prefs.edit().putString("playlist", serialized).commit() }
        }
    }
    fun play(c: Channel) {
        if (screen != "Reproductor") previousScreen = screen
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
                scope.launch {
                    busy = true
                    try {
                        val parsed = withContext(Dispatchers.IO) {
                            val content = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                            require(content.length <= 10_000_000) { "Archivo demasiado grande" }
                            readChannels(content)
                        }
                        if (parsed.isEmpty()) message = "No se encontraron canales compatibles."
                        else { savePlaylist(channels + parsed); message = "Importados ${parsed.size} canales" }
                    } catch (e: Exception) { message = "Error al importar: ${e.localizedMessage}" }
                    finally { busy = false }
                }
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
    fun navigateBack() {
        if (screen == "Inicio") showExitConfirmation = true
        else screen = if (screen == "Reproductor") previousScreen.takeUnless { it == "Reproductor" } ?: "TV en vivo" else "Inicio"
    }
    BackHandler { if (showMenu) showMenu = false else navigateBack() }
    Column(Modifier.fillMaxSize().background(Back)) {
        TmcHeader(screen, onBack = { navigateBack() }, onMenu = { showMenu = true }, onSearch = { screen = "Explorar" })
        Box(Modifier.weight(1f)) {
            when (screen) {
                "Inicio" -> LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text("TMCVIDEOS · TU ENTRETENIMIENTO", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Todo en videos, en un solo lugar", color = White, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 29.sp)
                        Spacer(Modifier.height(13.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("Inicio", "Películas", "Series", "Videos", "Deportes", "Música")) { tab ->
                                val active = tab == "Inicio"
                                Column(Modifier.width(90.dp).background(if (active) Red else Color(0xFF1C1C24), RoundedCornerShape(12.dp))
                                    .clickable { when (tab) { "Inicio" -> screen = "Inicio"; "Videos" -> screen = "Videos"; else -> { categoryFilter = tab; screen = "TV en vivo" } } }
                                    .padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(when(tab) { "Inicio" -> Icons.Default.Home; "Películas" -> Icons.Default.Movie; "Series" -> Icons.Default.Tv; "Videos" -> Icons.Default.PlayCircle; "Deportes" -> Icons.Default.SportsSoccer; else -> Icons.Default.MusicNote }, null, tint = White)
                                    Spacer(Modifier.height(5.dp))
                                    Text(tab, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HeroCard { categoryFilter = "Todos"; searching = ""; screen = "TV en vivo" }
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.height(12.dp))
                        NeonCard {
                            Row(Modifier.fillMaxWidth().clickable { screen = "Listas M3U" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Red, Purple)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlaylistAdd, "Agregar lista M3U", tint = White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Mis listas M3U", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(if (channels.isEmpty()) "Añade tu primera lista de canales" else "${channels.size} canales · Importar o administrar", color = Color.LightGray, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Gold)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Explora géneros", color = White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(11.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GenreCard("DEPORTES", "En acción", Icons.Default.SportsSoccer, Color(0xFF126A66), Modifier.weight(1f)) { categoryFilter = "Deportes"; searching = ""; screen = "TV en vivo" }
                            GenreCard("NOTICIAS", "Al instante", Icons.Default.Newspaper, Color(0xFF243D88), Modifier.weight(1f)) { categoryFilter = "Noticias"; searching = ""; screen = "TV en vivo" }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GenreCard("MÚSICA", "A tu ritmo", Icons.Default.MusicNote, Color(0xFF6B2B78), Modifier.weight(1f)) { categoryFilter = "Música"; searching = ""; screen = "TV en vivo" }
                            GenreCard("INFANTIL", "Para disfrutar", Icons.Default.ChildCare, Color(0xFF835027), Modifier.weight(1f)) { categoryFilter = "Infantil"; searching = ""; screen = "TV en vivo" }
                        }
                        Spacer(Modifier.height(18.dp))
                        if (history.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Seguir viendo", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { searching = ""; screen = "Historial" }) { Text("Ver historial", color = Gold) }
                            }
                            val recent = history.first()
                            val recentChannel = channels.find { it.url == recent }
                            NeonCard {
                                Row(Modifier.fillMaxWidth().clickable { play(recentChannel ?: Channel("Video reciente", recent, "Historial")) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).background(Brush.linearGradient(listOf(Blue, Purple)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.size(30.dp)) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(recentChannel?.name ?: "Continuar reproducción", color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Toca para reproducir", color = Color(0xFFABB6CF), fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = Gold)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                        NeonCard { Row(Modifier.fillMaxWidth().clickable { searching = ""; screen = "Historial" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = Gold); Spacer(Modifier.width(12.dp)); Text("Historial de reproducción", color = White, modifier = Modifier.weight(1f)); Text("${history.size}", color = Color.LightGray) } }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Canales para ti", color = White, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            TextButton(onClick = { categoryFilter = "Todos"; searching = ""; screen = "TV en vivo" }) { Text("Ver todos", color = Gold) }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (channels.isEmpty()) {
                            NeonCard {
                                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Tv, null, tint = Gold, modifier = Modifier.size(38.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("Tu catálogo comienza aquí", color = White, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Importa una lista M3U para mostrar tus canales.", color = Color.LightGray, fontSize = 12.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { screen = "Listas M3U" }) { Text("Importar lista") }
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(channels.take(16), key = { it.url }) { c ->
                                    PosterChannel(c, favorites.contains(c.url), { play(c) }, { toggleFavorite(c) })
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        if (favorites.isNotEmpty()) {
                            Text("Tus favoritos", color = White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(channels.filter { favorites.contains(it.url) }.take(16), key = { it.url }) { c ->
                                    PosterChannel(c, true, { play(c) }, { toggleFavorite(c) })
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                        NeonCard {
                            Row(Modifier.fillMaxWidth().clickable { screen = "Listas M3U" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlaylistAdd, null, tint = Gold, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Administrar listas M3U", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, tint = Gold)
                            }
                        }
                    }
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
                    NeonCard { Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { importFile.launch("*/*") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FileOpen, null, tint = Blue); Spacer(Modifier.width(12.dp)); Text("Importar archivo .m3u / .m3u8", color = White) } }
                    OutlinedTextField(urlInput, { urlInput = it }, label = { Text("URL de lista M3U (HTTP o HTTPS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        val candidate = urlInput.trim()
                        if (!isWebUrl(candidate)) { message = "Introduce un enlace HTTP o HTTPS válido"; return@Button }
                        scope.launch {
                            busy = true
                            try {
                                val found = withContext(Dispatchers.IO) { readChannels(downloadPlaylist(candidate)) }
                                if (found.isEmpty()) message = "No se detectaron canales M3U en la URL."
                                else { savePlaylist(channels + found); message = "Importados " + found.size + " canales"; urlInput = "" }
                            } catch (e: Exception) {
                                message = if (e.message?.contains("HTTP 403") == true) "HTTP 403: el servidor denegó el acceso." else "Error al descargar la lista: " + (e.localizedMessage ?: "Comprueba la conexión")
                            } finally { busy = false }
                        }
                    }, enabled = !busy && libraryLoaded, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Importando..." else "Agregar otra lista desde URL") }
                    if (busy || !libraryLoaded) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Puedes agregar más listas sin borrar los canales anteriores. Los enlaces repetidos se omiten.", color = Color.LightGray, fontSize = 12.sp)
                    if (channels.isNotEmpty()) {
                        Text("Editar canales importados", color = White, fontWeight = FontWeight.Bold)
                        channels.take(100).forEach { c ->
                            NeonCard {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(c.group, color = Color.LightGray, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = {
                                        editingChannel = c; editName = c.name; editGroup = c.group; editUrl = c.url
                                    }) { Icon(Icons.Default.Edit, "Editar canal", tint = Gold) }
                                }
                            }
                        }
                        if (channels.size > 100) Text("Se muestran los primeros 100 canales para editar.", color = Color.LightGray, fontSize = 12.sp)
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
                "Reproductor" -> selected?.let { c -> PlayerScreen(c, autoplay, diagnostics) { toggleFavorite(c) } }
                "Ajustes" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Configuración")
                    Text("Preferencias de reproducción", color = Gold, fontWeight = FontWeight.Bold)
                    NeonCard {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reproducción automática", color = White)
                                Text("Iniciar al abrir un canal", color = Color.LightGray, fontSize = 12.sp)
                            }
                            Switch(checked = autoplay, onCheckedChange = { autoplay = it; prefs.edit().putBoolean("autoplay", it).apply() })
                        }
                    }
                    NeonCard {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Diagnóstico de audio", color = White)
                                Text("Mostrar estado de la pista", color = Color.LightGray, fontSize = 12.sp)
                            }
                            Switch(checked = diagnostics, onCheckedChange = { diagnostics = it; prefs.edit().putBoolean("diagnostics", it).apply() })
                        }
                    }
                    Text("Biblioteca y datos", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { screen = "Listas M3U" }, modifier = Modifier.fillMaxWidth()) { Text("Administrar listas M3U") }
                    OutlinedButton(onClick = { searching = ""; screen = "Historial" }, modifier = Modifier.fillMaxWidth()) { Text("Ver historial") }
                    OutlinedButton(onClick = { searching = ""; screen = "Favoritos" }, modifier = Modifier.fillMaxWidth()) { Text("Ver favoritos") }
                    OutlinedButton(onClick = { history.clear(); prefs.edit().remove("history").apply(); message = "Historial eliminado" }, modifier = Modifier.fillMaxWidth()) { Text("Borrar historial") }
                    OutlinedButton(onClick = { favorites.clear(); prefs.edit().remove("favorites").apply(); message = "Favoritos eliminados" }, modifier = Modifier.fillMaxWidth()) { Text("Borrar favoritos") }
                    Text("TMCVIDEOS · Versión 2.0 · Media3", color = Color.Gray, fontSize = 12.sp)
                }
            }
            if (showMenu) AlertDialog(
                onDismissRequest = { showMenu = false },
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = Red); Spacer(Modifier.width(8.dp)); Text("TMCVIDEOS") } },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf("Inicio", "Explorar", "TV en vivo", "Videos", "Favoritos", "Historial", "Listas M3U", "Ajustes").forEach { destination ->
                            Row(Modifier.fillMaxWidth().clickable { showMenu = false; screen = destination }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when(destination) { "Inicio" -> Icons.Default.Home; "Explorar" -> Icons.Default.Explore; "TV en vivo" -> Icons.Default.LiveTv; "Videos" -> Icons.Default.VideoLibrary; "Favoritos" -> Icons.Default.Favorite; "Historial" -> Icons.Default.History; "Listas M3U" -> Icons.Default.PlaylistPlay; else -> Icons.Default.Settings }, null, tint = if (screen == destination) Red else White)
                                Spacer(Modifier.width(12.dp))
                                Text(destination, color = White)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Cerrar") } }
            )
            if (showExitConfirmation) AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                title = { Text("Salir de TMCVIDEOS") },
                text = { Text("¿Quieres cerrar la aplicación?") },
                confirmButton = { TextButton(onClick = { showExitConfirmation = false; activity.finish() }) { Text("Salir") } },
                dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("Cancelar") } }
            )
            editingChannel?.let { original ->
                AlertDialog(
                    onDismissRequest = { editingChannel = null },
                    title = { Text("Editar canal") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(editName, { editName = it }, label = { Text("Nombre") }, singleLine = true)
                            OutlinedTextField(editGroup, { editGroup = it }, label = { Text("Categoría") }, singleLine = true)
                            OutlinedTextField(editUrl, { editUrl = it }, label = { Text("URL de reproducción") }, singleLine = true)
                        }
                    },
                    confirmButton = { TextButton(onClick = {
                        if (editName.isBlank() || !isWebUrl(editUrl.trim())) message = "Escribe un nombre y una URL HTTP/HTTPS válida"
                        else {
                            savePlaylist(channels.map { if (it.url == original.url) original.copy(name = editName.trim(), group = editGroup.trim().ifBlank { "General" }, url = editUrl.trim()) else it })
                            editingChannel = null
                            message = "Canal actualizado"
                        }
                    }) { Text("Guardar") } },
                    dismissButton = { TextButton(onClick = { editingChannel = null }) { Text("Cancelar") } }
                )
            }
            if (showDeleteConfirmation) AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = { Text("Eliminar canales") }, text = { Text("Se eliminarán los canales importados de este dispositivo. ¿Continuar?") }, confirmButton = { TextButton(onClick = { savePlaylist(emptyList()); showDeleteConfirmation = false; message = "Canales eliminados" }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") } })
            if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("TMCVIDEOS") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("Aceptar") } })
        }
        NavigationBar(containerColor = Color(0xFF111728), tonalElevation = 0.dp) {
            listOf(Triple("Inicio", Icons.Default.Home, "Inicio"), Triple("TV", Icons.Default.LiveTv, "TV en vivo"), Triple("Listas", Icons.Default.PlaylistAdd, "Listas M3U"), Triple("Favoritos", Icons.Default.FavoriteBorder, "Favoritos"), Triple("Ajustes", Icons.Default.Settings, "Ajustes")).forEach { (label, icon, target) ->
                NavigationBarItem(selected = screen == target, onClick = { if (target == "TV en vivo") { categoryFilter = "Todos"; searching = "" }; if (target == "Favoritos") searching = ""; screen = target }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Red, selectedTextColor = Red, unselectedIconColor = White, unselectedTextColor = White))
            }
        }
    }
}

@Composable private fun TmcHeader(screen: String, onBack: () -> Unit, onMenu: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = if (screen == "Inicio") onMenu else onBack) {
            Icon(if (screen == "Inicio") Icons.Default.Menu else Icons.Default.ArrowBack, if (screen == "Inicio") "Menú" else "Volver", tint = White)
        }
        Box(Modifier.size(34.dp).background(Brush.linearGradient(listOf(Gold, Red)), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, "TMCVIDEOS", tint = White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("TMC", color = White, fontWeight = FontWeight.Black, fontSize = 22.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        Text("VIDEOS", color = Red, fontWeight = FontWeight.Black, fontSize = 22.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Buscar", tint = White) }
    }
}
@Composable private fun SectionTitle(s: String) {
    Text(s, color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
}
@Composable private fun NeonCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().border(1.dp, Color(0xFF2A3550), RoundedCornerShape(16.dp))
            .background(Surface, RoundedCornerShape(16.dp))
    ) { content() }
}
@Composable private fun MetricCard(
    value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(Color(0xFF161E31), RoundedCornerShape(13.dp))
            .border(1.dp, Color(0xFF26334D), RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(18.dp))
        Text(value, color = White, fontWeight = FontWeight.Black, fontSize = 21.sp)
        Text(label, color = Color(0xFF9FAAC0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun HeroCard(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(245.dp)
        .border(1.dp, Color(0xFF3C496B), RoundedCornerShape(23.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF17264A), Color(0xFF30224B), Color(0xFF7A2845))), RoundedCornerShape(23.dp))
        .clickable { onClick() }.padding(22.dp)) {
        Icon(Icons.Default.LiveTv, null, tint = White.copy(alpha = .11f), modifier = Modifier.align(Alignment.TopEnd).size(140.dp))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("● EN DIRECTO", color = Color(0xFF92FFD6), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("UNA NUEVA FORMA\nDE VER TV", color = White, fontWeight = FontWeight.Black, fontSize = 29.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Explora tus canales en un solo lugar.", color = Color(0xFFE5E8F6), fontSize = 12.sp)
            Spacer(Modifier.height(17.dp))
            Row(Modifier.background(White, RoundedCornerShape(11.dp)).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF171C33), modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(5.dp))
                Text("VER CANALES", color = Color(0xFF171C33), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
@Composable private fun GenreCard(label: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(128.dp).background(Brush.linearGradient(listOf(color, Color(0xFF1B243B))), RoundedCornerShape(17.dp)).clickable { onClick() }.padding(15.dp)) {
        Icon(icon, null, tint = White.copy(alpha = .55f), modifier = Modifier.align(Alignment.TopEnd).size(34.dp))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(label, color = White, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(subtitle, color = Color(0xFFD1D8E8), fontSize = 11.sp)
        }
    }
}
@Composable private fun CategoryPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Row(Modifier.border(1.dp, accent.copy(alpha = .45f), RoundedCornerShape(14.dp))
        .background(Color(0xFF192237), RoundedCornerShape(14.dp))
        .clickable { onClick() }.padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
        Text(label, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
@Composable private fun PosterChannel(c: Channel, favorite: Boolean, play: () -> Unit, toggle: () -> Unit) {
    Column(Modifier.width(155.dp).background(Color(0xFF161E31), RoundedCornerShape(16.dp)).clickable { play() }.padding(7.dp)) {
        Box(Modifier.fillMaxWidth().height(145.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF213F78), Color(0xFF48295F), Color(0xFF7A2946))), RoundedCornerShape(12.dp))) {
            if (c.logo.startsWith("https://") || c.logo.startsWith("http://")) {
                AsyncImage(model = c.logo, contentDescription = "Logo de " + c.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(100.dp).padding(12.dp))
            } else {
                Icon(Icons.Default.LiveTv, null, tint = White.copy(alpha = .22f), modifier = Modifier.align(Alignment.Center).size(70.dp))
            }
            Box(Modifier.align(Alignment.TopStart).padding(9.dp).background(Red, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text("EN VIVO", color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.PlayCircle, "Reproducir", tint = White, modifier = Modifier.align(Alignment.Center).size(43.dp))
            IconButton(onClick = toggle, modifier = Modifier.align(Alignment.TopEnd).size(38.dp)) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (favorite) Red else White)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(c.name, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(c.group, color = Color(0xFF9DACCB), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
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

@Composable private fun PlayerScreen(c: Channel, autoplay: Boolean, diagnostics: Boolean, toggle: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playbackError by remember(c.url) { mutableStateOf<String?>(null) }
    var audioStatus by remember(c.url) { mutableStateOf("Comprobando pista de audio...") }
    var volume by remember(c.url) { mutableFloatStateOf(1f) }
    var muted by remember(c.url) { mutableStateOf(false) }
    var trackInfo by remember(c.url) { mutableStateOf("Buscando formatos compatibles...") }
    val player = remember(c.url) { ExoPlayer.Builder(context).build().apply {
        volume = 1f
        addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                trackInfo = audioGroups.flatMap { group -> (0 until group.length).map { index -> group.getTrackFormat(index).sampleMimeType ?: "Desconocido" } }.distinct().joinToString(", ").ifBlank { "Sin pistas de audio" }
                audioStatus = when {
                    audioGroups.isEmpty() -> "Esta transmisión no anuncia una pista de audio. Puede ser una fuente sin sonido."
                    audioGroups.none { group -> (0 until group.length).any { group.isTrackSupported(it) } } -> "El dispositivo no admite el formato de audio de esta transmisión."
                    audioGroups.none { group -> (0 until group.length).any { group.isTrackSelected(it) } } -> "No hay una pista de audio seleccionada."
                    else -> "Pista de audio detectada y seleccionada."
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Error de reproducción (${error.errorCodeName}): ${error.cause?.localizedMessage ?: error.localizedMessage ?: "origen o formato incompatible"}"
            }
        })
        val uri = Uri.parse(c.url)
        val path = uri.path.orEmpty().lowercase()
        val mime = when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            else -> null
        }
        setMediaItem(MediaItem.Builder().setUri(uri).apply { if (mime != null) setMimeType(mime) }.build())
        prepare(); playWhenReady = autoplay
    } }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(240.dp))
        Column(Modifier.padding(16.dp)) {
            Text(c.name, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp)); Text(c.group, color = Color.LightGray)
            if (playbackError != null) Text(playbackError!!, color = Red, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("Audio", color = White, fontWeight = FontWeight.Bold)
            if (diagnostics) {
                Text(audioStatus, color = Color.LightGray, fontSize = 12.sp)
                Text("Formato de audio: " + trackInfo, color = Color(0xFF9DACCB), fontSize = 12.sp)
            }
            OutlinedButton(onClick = { playbackError = null; player.prepare(); player.play() }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reintentar reproducción") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { muted = !muted; player.volume = if (muted) volume else 0f }) {
                    Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Activar sonido" else "Silenciar", tint = White)
                }
                Slider(value = volume, onValueChange = { volume = it; if (!muted) player.volume = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                Text("${(volume * 100).toInt()}%", color = White, fontSize = 12.sp)
            }
            Text("Si la pista está detectada y sigue sin oírse, comprueba el volumen multimedia del teléfono y prueba otro canal.", color = Color.Gray, fontSize = 12.sp)
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
    var name = "Canal"; var group = "General"; var logo = ""
    for (line in lines) {
        when {
            line.startsWith("#EXTINF", true) -> {
                name = line.substringAfterLast(',', "Canal").trim().ifBlank { "Canal" }
                group = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1) ?: "General"
                logo = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.trim().orEmpty()
            }
            !line.startsWith("#") && (line.startsWith("https://") || line.startsWith("http://")) -> {
                if (output.size < 5000) output.add(Channel(name, line, group, logo = logo))
                name = "Canal"; group = "General"; logo = ""
            }
        }
    }
    return output.distinctBy { it.url }
}
private fun exportM3u(channels: List<Channel>): String = buildString {
    append("#EXTM3U\n")
    channels.forEach { c ->
        append("#EXTINF:-1 group-title=\"").append(c.group.replace("\"", "")).append("\"")
        if (c.logo.startsWith("https://") || c.logo.startsWith("http://")) append(" tvg-logo=\"").append(c.logo.replace("\"", "")).append("\"")
        append(",").append(c.name.replace("\n", " ")).append("\n")
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
