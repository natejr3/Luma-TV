package com.lumatv.app.data

data class EmbySession(
    val serverUrl: String,
    val userId: String,
    val accessToken: String,
    val username: String,
)

data class MediaItem(
    val id: String,
    val name: String,
    val type: String,
    val overview: String = "",
    val year: Int? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val genres: List<String> = emptyList(),
    val imageTag: String? = null,
    val backdropTag: String? = null,
    val playedPercentage: Double? = null,
    val isFavorite: Boolean = false,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val indexNumber: Int? = null,
) {
    val isEpisode: Boolean get() = type.equals("Episode", true)
    val subtitle: String
        get() = when {
            isEpisode && seriesName != null -> listOfNotNull(seriesName, seasonName, indexNumber?.let { "Episode $it" }).joinToString(" • ")
            year != null -> year.toString()
            else -> type
        }
}

data class CatalogRow(val title: String, val items: List<MediaItem>)

data class PlaybackSource(val uri: String, val isDirectPlay: Boolean)

data class HomeCatalog(
    val hero: MediaItem? = null,
    val rows: List<CatalogRow> = emptyList(),
)

sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class Ready(val session: EmbySession, val catalog: HomeCatalog = HomeCatalog()) : AppState
    data class Error(val message: String) : AppState
}
