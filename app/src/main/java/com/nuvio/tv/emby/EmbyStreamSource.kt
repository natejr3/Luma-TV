package com.nuvio.tv.emby

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbyStreamSource"
private const val SERVER_NAME = "Omega"
private const val MATCH_CACHE_TTL_MS = 5 * 60 * 1000L

@Singleton
class EmbyStreamSource @Inject constructor(
    @ApplicationContext context: Context,
    private val tmdbService: TmdbService,
) {
    private val client = EmbyClient()
    private val sessionStore = EmbySessionStore(context.applicationContext)
    private val matchCache = ConcurrentHashMap<String, Pair<Long, List<EmbyItem>>>()
    private val episodeCache = ConcurrentHashMap<String, Pair<Long, List<EmbyItem>>>()

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
        val cacheKey = "${session.userId}|$normalizedType|$baseId"
        val matched = cached(matchCache, cacheKey) ?: run {
            val directImdb = baseId.takeIf { it.startsWith("tt", ignoreCase = true) }
            val tmdbId = runCatching { tmdbService.ensureTmdbId(baseId, normalizedType) }.getOrNull()

            // Fast path: most libraries already carry a TMDB provider id. Avoid the extra
            // TMDB->IMDb network request unless the TMDB lookup fails to find Omega content.
            var found = runCatching {
                client.findMatchingItems(session, normalizedType, tmdbId, directImdb)
            }.onFailure { Log.w(TAG, "Omega match failed for $videoId: ${it.message}") }
                .getOrDefault(emptyList())

            if (found.isEmpty() && directImdb == null && tmdbId != null) {
                val fallbackImdb = runCatching {
                    tmdbService.tmdbToImdb(tmdbId.toInt(), normalizedType)
                }.getOrNull()
                if (!fallbackImdb.isNullOrBlank()) {
                    found = runCatching {
                        client.findMatchingItems(session, normalizedType, null, fallbackImdb)
                    }.getOrDefault(emptyList())
                }
            }
            putCached(matchCache, cacheKey, found)
            found
        }

        if (matched.isEmpty()) return emptyList()

        val playableItems = if (normalizedType == "series" && season != null && episode != null) {
            matched.flatMap { series ->
                val key = "${session.userId}|${series.id}"
                val episodes = cached(episodeCache, key) ?: run {
                    val loaded = runCatching { client.loadSeriesEpisodes(session, series.id) }
                        .getOrDefault(emptyList())
                    putCached(episodeCache, key, loaded)
                    loaded
                }
                episodes.filter { it.parentIndexNumber == season && it.indexNumber == episode }
            }
        } else {
            matched
        }

        val streams = playableItems.distinctBy { it.id }.take(4).mapNotNull { item ->
            val source = runCatching { client.playbackSource(session, item) }
                .onFailure { Log.w(TAG, "Omega playback resolve failed for ${item.name}: ${it.message}") }
                .getOrNull() ?: return@mapNotNull null

            val fileName = source.fileName?.takeIf { it.isNotBlank() } ?: item.name
            val sizeLabel = source.fileSizeBytes?.let(::formatFileSize)
            val details = listOfNotNull(sizeLabel, fileName).joinToString(" • ")

            Stream(
                name = SERVER_NAME,
                title = details.ifBlank { fileName },
                description = details.ifBlank { "$SERVER_NAME • $fileName" },
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
                    filename = fileName,
                ),
                addonName = SERVER_NAME,
                addonLogo = null,
                sources = listOf(SERVER_NAME),
            )
        }

        return if (streams.isEmpty()) emptyList() else listOf(
            AddonStreams(
                addonName = SERVER_NAME,
                addonLogo = null,
                streams = streams,
            )
        )
    }

    private fun cached(
        cache: ConcurrentHashMap<String, Pair<Long, List<EmbyItem>>>,
        key: String,
    ): List<EmbyItem>? {
        val value = cache[key] ?: return null
        if (System.currentTimeMillis() - value.first > MATCH_CACHE_TTL_MS) {
            cache.remove(key)
            return null
        }
        return value.second
    }

    private fun putCached(
        cache: ConcurrentHashMap<String, Pair<Long, List<EmbyItem>>>,
        key: String,
        items: List<EmbyItem>,
    ) {
        if (cache.size > 96) cache.clear()
        cache[key] = System.currentTimeMillis() to items
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
