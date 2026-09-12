package com.lumatv.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumatv.app.data.AppState
import com.lumatv.app.data.EmbyClient
import com.lumatv.app.data.EmbySession
import com.lumatv.app.data.MediaItem
import com.lumatv.app.data.SecureSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LumaViewModel(application: Application) : AndroidViewModel(application) {
    private val client = EmbyClient()
    private val sessionStore = SecureSessionStore(application)
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state = _state.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    init {
        val saved = sessionStore.load()
        if (saved == null) _state.value = AppState.SignedOut else refresh(saved)
    }

    fun login(server: String, username: String, password: String) {
        viewModelScope.launch {
            _state.value = AppState.Loading
            runCatching { client.authenticate(server, username, password) }
                .onSuccess { sessionStore.save(it); refresh(it) }
                .onFailure { _state.value = AppState.Error(it.friendlyMessage()) }
        }
    }

    fun refresh(session: EmbySession? = (_state.value as? AppState.Ready)?.session) {
        if (session == null) return
        viewModelScope.launch {
            _state.value = AppState.Loading
            runCatching { client.loadHome(session) }
                .onSuccess { _state.value = AppState.Ready(session, it) }
                .onFailure { _state.value = AppState.Error(it.friendlyMessage()) }
        }
    }

    fun search(query: String) {
        val session = (_state.value as? AppState.Ready)?.session ?: return
        viewModelScope.launch {
            _searchResults.value = runCatching { client.search(session, query) }.getOrElse { emptyList() }
        }
    }

    fun imageUrl(session: EmbySession, item: MediaItem, backdrop: Boolean = false) = client.imageUrl(session, item, backdrop)
    fun streamUrl(session: EmbySession, item: MediaItem) = client.streamUrl(session, item)
    suspend fun playbackSource(session: EmbySession, item: MediaItem) = client.playbackSource(session, item)

    fun logout() {
        sessionStore.clear()
        _searchResults.value = emptyList()
        _state.value = AppState.SignedOut
    }

    fun returnToLogin() { _state.value = AppState.SignedOut }

    private fun Throwable.friendlyMessage(): String = when {
        message?.contains("401") == true -> "That username or password was not accepted."
        message?.contains("Unable to resolve host") == true -> "The TV cannot find that server. Check the address and Wi-Fi."
        message?.contains("timeout", true) == true -> "The server took too long to respond."
        else -> message ?: "Could not connect to Emby."
    }
}
