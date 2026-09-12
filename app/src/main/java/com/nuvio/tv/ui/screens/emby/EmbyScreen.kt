package com.nuvio.tv.ui.screens.emby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.emby.EmbyClient
import com.nuvio.tv.emby.EmbyPlaybackSource
import com.nuvio.tv.emby.EmbySession
import com.nuvio.tv.emby.EmbySessionStore
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

@Composable
fun EmbyScreen(
    onBack: () -> Unit,
    onPlay: (EmbyPlaybackSource, EmbySession) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { EmbySessionStore(context.applicationContext) }
    val client = remember(context) { EmbyClient.forDevice(context) }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<EmbySession?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        session = store.load()
        loading = false
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(620.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "omega",
                color = NuvioTheme.colors.TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Emby connection",
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(28.dp))

            val active = session
            if (active != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF73D98B),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Connected to omega",
                    color = NuvioTheme.colors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = active.username.ifBlank { "Emby user" },
                    color = NuvioTheme.colors.TextSecondary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Luma scans omega automatically when you open movies and shows.",
                    color = NuvioTheme.colors.TextSecondary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text("Back")
                    }
                    Button(onClick = {
                        store.clear()
                        session = null
                        error = null
                    }) {
                        Icon(Icons.Default.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text("Disconnect")
                    }
                }
            } else {
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
                        if (server.isNotBlank() && username.isNotBlank() && !loading) {
                            scope.launch {
                                loading = true
                                error = null
                                runCatching { client.authenticate(server, username, password) }
                                    .onSuccess {
                                        store.save(it)
                                        session = it
                                        password = ""
                                    }
                                    .onFailure { error = it.message ?: "Could not connect to omega." }
                                loading = false
                            }
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text("Back")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                runCatching { client.authenticate(server, username, password) }
                                    .onSuccess {
                                        store.save(it)
                                        session = it
                                        password = ""
                                    }
                                    .onFailure { error = it.message ?: "Could not connect to omega." }
                                loading = false
                            }
                        },
                        enabled = !loading && server.isNotBlank() && username.isNotBlank(),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            androidx.compose.material3.Text("Connect omega", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
