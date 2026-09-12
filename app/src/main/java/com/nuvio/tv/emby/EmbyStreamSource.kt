package com.nuvio.tv.emby

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbyStreamSource"

@Singleton
class EmbyStreamSource @Inject constructor(
    @ApplicationContext context: Context,
    private val tmdbService: TmdbService,
) {
    private val client = EmbyClient()
    private val sessionStore = EmbySessionStore(context.applicationContext)

    suspend fun streamsFor(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
    ): List<AddonStreams> {
        val session = sessionStore.load() ?: return emptyList()
        val normalizedType = when (type.lowercase()) {
            "series", "tv", "show" -> "series"
            "movie" -> "movie"
            else -> return emptyList()
        }

        val baseId = videoId.substringBefore(':').substringBefore('/').trim()
        val tmdbId = runCatching { tmdbService.ensureTmdbId(baseId, normalizedType) }.getOrNull()
        val imdbId = when {
            baseId.startsWith("tt", ignoreCase = true) -> baseId
            tmdbId != null -> runCatching {
                tmdbService.tmdbToImdb(tmdbId.toInt(), normalizedType)
            }.getOrNull()
            else -> null
        }

        val matched = runCatching {
            client.findMatchingItems(session, normalizedType, tmdbId, imdbId)
        }.onFailure { Log.w(TAG, "Emby match failed for $videoId: ${it.message}") }
            .getOrDefault(emptyList())

        if (matched.isEmpty()) return emptyList()

        val playableItems = if (normalizedType == "series" && season != null && episode != null) {
            matched.flatMap { series ->
                runCatching { client.loadSeriesEpisodes(session, series.id) }
                    .getOrDefault(emptyList())
                    .filter { it.parentIndexNumber == season && it.indexNumber == episode }
            }
        } else {
            matched
        }

        val streams = playableItems.distinctBy { it.id }.take(12).mapNotNull { item ->
            val source = runCatching { client.playbackSource(session, item) }
                .onFailure { Log.w(TAG, "Emby playback resolve failed for ${item.name}: ${it.message}") }
                .getOrNull() ?: return@mapNotNull null

            Stream(
                name = "Emby",
                title = item.name,
                description = buildString {
                    append("Emby")
                    item.year?.let { append(" • $it") }
                    if (source.isDirectPlay) append(" • Direct Play") else append(" • Transcode")
                },
                url = source.uri,
                ytId = null,
                infoHash = null,
                fileIdx = null,
                externalUrl = null,
                behaviorHints = StreamBehaviorHints(
                    notWebReady = false,
                    bingeGroup = null,
                    countryWhitelist = null,
                    proxyHeaders = ProxyHeaders(
                        request = source.headers,
                        response = null,
                    ),
                    filename = item.name,
                ),
                addonName = "Emby",
                addonLogo = null,
                sources = listOf("Emby"),
            )
        }

        return if (streams.isEmpty()) emptyList() else listOf(
            AddonStreams(
                addonName = "Emby",
                addonLogo = null,
                streams = streams,
            )
        )
    }
}
