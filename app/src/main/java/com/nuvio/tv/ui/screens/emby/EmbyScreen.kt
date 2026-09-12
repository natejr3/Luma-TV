package com.nuvio.tv.ui.screens.emby

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.emby.EmbyClient
import com.nuvio.tv.emby.EmbyHomeCatalog
import com.nuvio.tv.emby.EmbyItem
import com.nuvio.tv.emby.EmbyPlaybackSource
import com.nuvio.tv.emby.EmbySession
import com.nuvio.tv.emby.EmbySessionStore
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

private sealed interface EmbyDestination {
    data object Home : EmbyDestination
    data object Search : EmbyDestination
    data class Details(val item: EmbyItem) : EmbyDestination
}

@Composable
fun EmbyScreen(
    onBack: () -> Unit,
    onPlay: (EmbyPlaybackSource, EmbySession) -> Unit,
) {
    val context = LocalContext.current
    val client = remember { EmbyClient() }
    val store = remember(context) { EmbySessionStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<EmbySession?>(null) }
    var catalog by remember { mutableStateOf(EmbyHomeCatalog()) }
    var destination by remember { mutableStateOf<EmbyDestination>(EmbyDestination.Home) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playbackLoading by remember { mutableStateOf(false) }

    fun refresh(activeSession: EmbySession) {
        scope.launch {
            loading = true
            error = null
            runCatching { client.loadHome(activeSession) }
                .onSuccess { catalog = it }
                .onFailure { error = it.message ?: "Could not load your Emby library." }
            loading = false
        }
    }

    fun play(item: EmbyItem) {
        val activeSession = session ?: return
        scope.launch {
            playbackLoading = true
            error = null
            runCatching { client.playbackSource(activeSession, item) }
                .onSuccess { onPlay(it, activeSession) }
                .onFailure { error = it.message ?: "Could not start playback." }
            playbackLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val saved = store.load()
        session = saved
        if (saved != null) {
            runCatching { client.loadHome(saved) }
                .onSuccess { catalog = it }
                .onFailure { error = it.message ?: "Could not connect to Emby." }
        }
        loading = false
    }

    BackHandler(enabled = destination !is EmbyDestination.Home) {
        destination = EmbyDestination.Home
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        when {
            session == null -> EmbyLogin(
                loading = loading,
                error = error,
                onBack = onBack,
                onLogin = { server, username, password ->
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { client.authenticate(server, username, password) }
                            .onSuccess { signedIn ->
                                store.save(signedIn)
                                session = signedIn
                                runCatching { client.loadHome(signedIn) }
                                    .onSuccess { catalog = it }
                                    .onFailure { error = it.message ?: "Connected, but the library could not be loaded." }
                            }
                            .onFailure { error = friendlyError(it) }
                        loading = false
                    }
                }
            )
            loading && catalog.rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                val activeSession = session!!
                when (val current = destination) {
                    EmbyDestination.Home -> EmbyHome(
                        session = activeSession,
                        catalog = catalog,
                        client = client,
                        error = error,
                        playbackLoading = playbackLoading,
                        onDetails = { destination = EmbyDestination.Details(it) },
                        onSearch = { destination = EmbyDestination.Search },
                        onRefresh = { refresh(activeSession) },
                        onLogout = {
                            store.clear()
                            session = null
                            catalog = EmbyHomeCatalog()
                            destination = EmbyDestination.Home
                        },
                    )
                    EmbyDestination.Search -> EmbySearch(
                        session = activeSession,
                        client = client,
                        onBack = { destination = EmbyDestination.Home },
                        onDetails = { destination = EmbyDestination.Details(it) },
                    )
                    is EmbyDestination.Details -> EmbyDetails(
                        session = activeSession,
                        item = current.item,
                        client = client,
                        playbackLoading = playbackLoading,
                        onBack = { destination = EmbyDestination.Home },
                        onPlay = ::play,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbyLogin(
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onLogin: (String, String, String) -> Unit,
) {
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(NuvioTheme.colors.Primary.copy(alpha = .22f), NuvioTheme.colors.Background),
                    radius = 1100f,
                )
            )
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).width(600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("EMBY", color = NuvioTheme.colors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("Connect your server to Luma TV", color = NuvioTheme.colors.TextSecondary, fontSize = 18.sp)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { androidx.compose.material3.Text("Server address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { androidx.compose.material3.Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { androidx.compose.material3.Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (server.isNotBlank() && username.isNotBlank()) onLogin(server, username, password)
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Text("Back")
                }
                Button(
                    onClick = { onLogin(server, username, password) },
                    enabled = !loading && server.isNotBlank() && username.isNotBlank(),
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else androidx.compose.material3.Text("Connect to Emby", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmbyHome(
    session: EmbySession,
    catalog: EmbyHomeCatalog,
    client: EmbyClient,
    error: String?,
    playbackLoading: Boolean,
    onDetails: (EmbyItem) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    var hero by remember(catalog) { mutableStateOf(catalog.hero) }
    Box(Modifier.fillMaxSize()) {
        hero?.let { item ->
            AsyncImage(
                model = client.imageUrl(session, item, backdrop = true) ?: client.imageUrl(session, item),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.15f), NuvioTheme.colors.Background.copy(.72f), NuvioTheme.colors.Background), endY = 950f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(NuvioTheme.colors.Background.copy(.94f), Color.Transparent), endX = 1000f)))

        LazyColumn(contentPadding = PaddingValues(bottom = 52.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 54.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Emby", color = NuvioTheme.colors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    HeaderButton(Icons.Default.Search, "Search", onSearch)
                    Spacer(Modifier.width(10.dp))
                    HeaderButton(Icons.Default.Refresh, "Refresh", onRefresh)
                    Spacer(Modifier.width(10.dp))
                    HeaderButton(Icons.Default.Logout, "Disconnect", onLogout)
                }
                hero?.let { item ->
                    Column(
                        Modifier.height(300.dp).padding(start = 54.dp, top = 20.dp).width(690.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(item.name, color = NuvioTheme.colors.TextPrimary, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOfNotNull(item.year?.toString(), item.officialRating, item.communityRating?.let { "★ %.1f".format(it) }).joinToString("  •  "),
                            color = NuvioTheme.colors.TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(item.overview, color = NuvioTheme.colors.TextSecondary, fontSize = 16.sp, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { onDetails(item) }) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Text("View details", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 54.dp, vertical = 8.dp))
                }
                if (playbackLoading) {
                    Row(Modifier.padding(horizontal = 54.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Preparing playback…", color = NuvioTheme.colors.TextSecondary)
                    }
                }
            }
            items(catalog.rows, key = { it.title }) { row ->
                Column(Modifier.padding(bottom = 28.dp)) {
                    Text(row.title, color = NuvioTheme.colors.TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 54.dp, vertical = 10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 54.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(row.items, key = { row.title + it.id }) { item ->
                            EmbyPoster(session, item, client, onFocused = { hero = item }, onClick = { onDetails(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbyDetails(
    session: EmbySession,
    item: EmbyItem,
    client: EmbyClient,
    playbackLoading: Boolean,
    onBack: () -> Unit,
    onPlay: (EmbyItem) -> Unit,
) {
    var episodes by remember(item.id) { mutableStateOf<List<EmbyItem>>(emptyList()) }
    var loadingEpisodes by remember(item.id) { mutableStateOf(item.isSeries) }
    LaunchedEffect(item.id) {
        if (item.isSeries) {
            episodes = runCatching { client.loadSeriesEpisodes(session, item.id) }.getOrDefault(emptyList())
            loadingEpisodes = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = client.imageUrl(session, item, true) ?: client.imageUrl(session, item),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(NuvioTheme.colors.Background, NuvioTheme.colors.Background.copy(.72f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, NuvioTheme.colors.Background))))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Column(Modifier.padding(start = 58.dp, top = 72.dp).width(700.dp)) {
                    Text(item.name, color = NuvioTheme.colors.TextPrimary, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        listOfNotNull(item.subtitle, item.officialRating, item.communityRating?.let { "★ %.1f".format(it) }).joinToString("  •  "),
                        color = NuvioTheme.colors.TextSecondary,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(item.overview.ifBlank { "No description available." }, color = NuvioTheme.colors.TextSecondary, fontSize = 18.sp, lineHeight = 27.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onPlay(item) }, enabled = !playbackLoading) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Text(if ((item.playedPercentage ?: 0.0) > 0) "Resume" else "Play", fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Text("Back")
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
            if (item.isSeries) {
                item {
                    Text("Episodes", color = NuvioTheme.colors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 58.dp, vertical = 10.dp))
                    if (loadingEpisodes) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 58.dp).size(22.dp), strokeWidth = 2.dp)
                    } else if (episodes.isEmpty()) {
                        Text("No episodes found.", color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(horizontal = 58.dp))
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 58.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(episodes, key = { it.id }) { episode ->
                                EmbyPoster(session, episode, client, onClick = { onPlay(episode) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbySearch(
    session: EmbySession,
    client: EmbyClient,
    onBack: () -> Unit,
    onDetails: (EmbyItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EmbyItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            results = runCatching { client.search(session, query) }.getOrDefault(emptyList())
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(Icons.Default.ArrowBack, "Emby", onBack)
            Spacer(Modifier.width(18.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { androidx.compose.material3.Text("Search your Emby library") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.width(650.dp),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { runSearch() }, enabled = !loading && query.isNotBlank()) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Text("Search")
            }
        }
        Spacer(Modifier.height(28.dp))
        if (loading) CircularProgressIndicator()
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(results, key = { it.id }) { item ->
                EmbyPoster(session, item, client, onClick = { onDetails(item) })
            }
        }
    }
}

@Composable
private fun HeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = .12f),
            contentColor = NuvioTheme.colors.TextPrimary,
        ),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(label)
    }
}

@Composable
private fun EmbyPoster(
    session: EmbySession,
    item: EmbyItem,
    client: EmbyClient,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(130), label = "embyCardScale")
    Column(
        Modifier
            .width(178.dp)
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .background(NuvioTheme.colors.SurfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth().height(255.dp).background(NuvioTheme.colors.Surface)) {
            AsyncImage(
                model = client.imageUrl(session, item),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            item.playedPercentage?.takeIf { it in 1.0..99.9 }?.let { progress ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth((progress / 100.0).toFloat())
                        .height(4.dp)
                        .background(NuvioTheme.colors.Primary)
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.name, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = NuvioTheme.colors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun friendlyError(error: Throwable): String = when {
    error.message?.contains("401") == true -> "That username or password was not accepted."
    error.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "The TV cannot find that server. Check the address and Wi-Fi."
    error.message?.contains("timeout", ignoreCase = true) == true -> "The server took too long to respond."
    else -> error.message ?: "Could not connect to Emby."
}
