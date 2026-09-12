package com.nuvio.tv.emby

data class EmbySession(
    val serverUrl: String,
    val userId: String,
    val accessToken: String,
    val username: String,
)

data class EmbyItem(
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
    val parentIndexNumber: Int? = null,
) {
    val isEpisode: Boolean get() = type.equals("Episode", true)
    val isSeries: Boolean get() = type.equals("Series", true)
    val subtitle: String
        get() = when {
            isEpisode && seriesName != null -> listOfNotNull(
                seriesName,
                parentIndexNumber?.let { "S$it" },
                indexNumber?.let { "E$it" },
            ).joinToString(" • ")
            year != null -> year.toString()
            else -> type
        }
}

data class EmbyRow(val title: String, val items: List<EmbyItem>)

data class EmbyPlaybackSource(
    val uri: String,
    val headers: Map<String, String>,
    val item: EmbyItem,
    val isDirectPlay: Boolean,
)

data class EmbyHomeCatalog(
    val hero: EmbyItem? = null,
    val rows: List<EmbyRow> = emptyList(),
)
