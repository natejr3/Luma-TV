package com.lumatv.app.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.lumatv.app.LumaViewModel
import com.lumatv.app.data.AppState
import com.lumatv.app.data.EmbySession
import com.lumatv.app.data.HomeCatalog
import com.lumatv.app.data.MediaItem
import com.lumatv.app.data.PlaybackSource

private val Ink = Color(0xFF050609)
private val Panel = Color(0xFF141720)
private val Violet = Color(0xFF8A72FF)
private val Frost = Color(0xFFF5F7FF)

private sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Details(val item: MediaItem) : Screen
    data class Player(val item: MediaItem) : Screen
}

@Composable
fun LumaApp(state: AppState, searchResults: List<MediaItem>, vm: LumaViewModel) {
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Frost)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            when (state) {
                AppState.Loading -> LoadingScreen()
                AppState.SignedOut -> LoginScreen(vm::login)
                is AppState.Error -> ErrorScreen(state.message, vm::returnToLogin)
                is AppState.Ready -> SignedInApp(state.session, state.catalog, searchResults, vm)
            }
        }
    }
}

@Composable
private fun SignedInApp(session: EmbySession, catalog: HomeCatalog, searchResults: List<MediaItem>, vm: LumaViewModel) {
    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }
    AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "screen") { destination ->
        when (destination) {
            Screen.Home -> HomeScreen(session, catalog, vm, { screen = Screen.Details(it) }, { screen = Screen.Search }, { screen = Screen.Settings })
            Screen.Search -> SearchScreen(session, searchResults, vm, { screen = Screen.Details(it) }, { screen = Screen.Home })
            Screen.Settings -> SettingsScreen(session, { vm.refresh(session); screen = Screen.Home }, vm::logout, { screen = Screen.Home })
            is Screen.Details -> DetailsScreen(session, destination.item, vm, { screen = Screen.Player(destination.item) }, { screen = Screen.Home })
            is Screen.Player -> PlayerScreen(session, destination.item, vm) { screen = Screen.Details(destination.item) }
        }
    }
}

@Composable
private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Violet)
        Spacer(Modifier.height(20.dp))
        Text("Loading your library…", color = Frost)
    }
}

@Composable
private fun ErrorScreen(message: String, back: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(520.dp)) {
        Text("Couldn’t connect", color = Frost, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.White.copy(alpha = .7f))
        Spacer(Modifier.height(24.dp))
        Button(onClick = back) { Text("Back to sign in") }
    }
}

@Composable
private fun LoginScreen(login: (String, String, String) -> Unit) {
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF242057), Ink), radius = 1100f))) {
        Column(Modifier.align(Alignment.Center).width(560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LUMA", color = Frost, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
            Text("Your library, beautifully focused.", color = Color.White.copy(alpha = .6f), fontSize = 18.sp)
            Spacer(Modifier.height(38.dp))
            LumaField(server, { server = it }, "Emby server address", ImeAction.Next)
            Spacer(Modifier.height(12.dp))
            LumaField(username, { username = it }, "Username", ImeAction.Next)
            Spacer(Modifier.height(12.dp))
            LumaField(password, { password = it }, "Password", ImeAction.Done, true) { login(server, username, password) }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { login(server, username, password) },
                enabled = server.isNotBlank() && username.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Frost, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Connect to Emby", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            Text("Use HTTPS when connecting outside your home network.", color = Color.White.copy(alpha = .45f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun LumaField(value: String, change: (String) -> Unit, label: String, action: ImeAction, password: Boolean = false, done: () -> Unit = {}) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = action),
        keyboardActions = KeyboardActions(onDone = { done() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeScreen(session: EmbySession, catalog: HomeCatalog, vm: LumaViewModel, details: (MediaItem) -> Unit, search: () -> Unit, settings: () -> Unit) {
    var hero by remember(catalog) { mutableStateOf(catalog.hero) }
    Box(Modifier.fillMaxSize()) {
        hero?.let { item ->
            AsyncImage(
                model = vm.imageUrl(session, item, true) ?: vm.imageUrl(session, item),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.18f), Ink.copy(.58f), Ink), startY = 0f, endY = 900f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink.copy(.92f), Color.Transparent), endX = 1000f)))

        LazyColumn(contentPadding = PaddingValues(bottom = 52.dp)) {
            item {
                Header(search = search, settings = settings)
                Hero(hero, details)
            }
            items(catalog.rows) { row ->
                Column(Modifier.padding(bottom = 28.dp)) {
                    Text(row.title, color = Frost, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 54.dp, vertical = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 54.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(row.items, key = { row.title + it.id }) { item ->
                            PosterCard(session, item, vm, onFocused = { hero = item }, onClick = { details(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(search: () -> Unit, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 54.dp, vertical = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("LUMA", color = Frost, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Spacer(Modifier.weight(1f))
        NavButton(Icons.Default.Search, "Search", search)
        Spacer(Modifier.width(10.dp))
        NavButton(Icons.Default.Settings, "Settings", settings)
    }
}

@Composable
private fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
    Button(onClick = click, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(.12f), contentColor = Frost)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    }
}

@Composable
private fun Hero(item: MediaItem?, details: (MediaItem) -> Unit) {
    Column(Modifier.height(330.dp).padding(start = 54.dp, top = 26.dp).width(650.dp), verticalArrangement = Arrangement.Center) {
        if (item == null) return@Column
        Text(item.name, color = Frost, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Text(listOfNotNull(item.year?.toString(), item.officialRating, item.communityRating?.let { "★ %.1f".format(it) }).joinToString("  •  "), color = Color.White.copy(.75f))
        Spacer(Modifier.height(12.dp))
        Text(item.overview, color = Color.White.copy(.72f), fontSize = 16.sp, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { details(item) }, colors = ButtonDefaults.buttonColors(containerColor = Frost, contentColor = Ink)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("View details", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PosterCard(session: EmbySession, item: MediaItem, vm: LumaViewModel, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(130), label = "cardScale")
    Column(
        Modifier.width(176.dp).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).focusable().background(Panel),
    ) {
        Box(Modifier.fillMaxWidth().height(255.dp).background(Color(0xFF20232D))) {
            AsyncImage(model = vm.imageUrl(session, item), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            item.playedPercentage?.takeIf { it in 1.0..99.9 }?.let { progress ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.toFloat() / 100f).height(4.dp).background(Violet))
            }
        }
        Column(Modifier.padding(11.dp)) {
            Text(item.name, color = Frost, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = Color.White.copy(.48f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailsScreen(session: EmbySession, item: MediaItem, vm: LumaViewModel, play: () -> Unit, back: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = vm.imageUrl(session, item, true) ?: vm.imageUrl(session, item), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink, Ink.copy(.7f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
        Column(Modifier.align(Alignment.CenterStart).padding(64.dp).width(650.dp)) {
            Text(item.name, color = Frost, fontSize = 50.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(listOfNotNull(item.subtitle, item.officialRating, item.communityRating?.let { "★ %.1f".format(it) }).joinToString("  •  "), color = Color.White.copy(.76f), fontSize = 17.sp)
            Spacer(Modifier.height(18.dp))
            Text(item.overview.ifBlank { "No description available." }, color = Color.White.copy(.78f), fontSize = 18.sp, lineHeight = 27.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(26.dp))
            Row {
                Button(onClick = play, colors = ButtonDefaults.buttonColors(containerColor = Frost, contentColor = Ink), modifier = Modifier.height(54.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if ((item.playedPercentage ?: 0.0) > 0) "Resume" else "Play", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Button(onClick = back, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(.13f))) { Text("Back") }
            }
        }
    }
}

@Composable
private fun SearchScreen(session: EmbySession, results: List<MediaItem>, vm: LumaViewModel, details: (MediaItem) -> Unit, back: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavButton(Icons.Default.Home, "Home", back)
            Spacer(Modifier.width(18.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search your Emby library") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                modifier = Modifier.width(620.dp),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { vm.search(query) }) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(7.dp)); Text("Search") }
        }
        Spacer(Modifier.height(34.dp))
        if (results.isEmpty()) Text("Type a movie, show, or episode name.", color = Color.White.copy(.55f), fontSize = 20.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items(results, key = { it.id }) { item -> PosterCard(session, item, vm, {}, { details(item) }) }
        }
    }
}

@Composable
private fun SettingsScreen(session: EmbySession, refresh: () -> Unit, logout: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(60.dp)) {
        Text("Settings", color = Frost, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text("Connected as ${session.username}", color = Frost, fontSize = 21.sp)
        Text(session.serverUrl, color = Color.White.copy(.55f))
        Spacer(Modifier.height(30.dp))
        Row {
            Button(onClick = refresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(7.dp)); Text("Refresh library") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = logout, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7D2430))) { Text("Disconnect server") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = back) { Text("Back") }
        }
    }
}

@Composable
private fun PlayerScreen(session: EmbySession, item: MediaItem, vm: LumaViewModel, back: () -> Unit) {
    val context = LocalContext.current
    var source by remember(item.id) { mutableStateOf<PlaybackSource?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        runCatching { vm.playbackSource(session, item) }
            .onSuccess { source = it }
            .onFailure { error = it.message ?: "Playback could not start." }
    }
    BackHandler(onBack = back)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val playback = source
        if (playback != null) {
            val player = remember(playback.uri) {
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5_000, 30_000, 1_500, 3_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
                val renderers = DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                ExoPlayer.Builder(context, renderers).setLoadControl(loadControl).build().apply {
                    setMediaItem(ExoMediaItem.fromUri(playback.uri))
                    playWhenReady = true
                    prepare()
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        controllerShowTimeoutMs = 3_000
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            Text(if (playback.isDirectPlay) "DIRECT PLAY" else "EMBY OPTIMIZED", color = Color.White.copy(.55f), fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).background(Color.Black.copy(.45f), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
        } else if (error != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = Frost); Spacer(Modifier.height(16.dp)); Button(onClick = back) { Text("Go back") }
            }
        } else CircularProgressIndicator(Modifier.align(Alignment.Center), color = Violet)
    }
}
