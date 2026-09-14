package com.nuvio.tv.emby

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbyStreamSource"
private const val SERVER_NAME = "omega"
private const val MATCH_CACHE_TTL_MS = 5 * 60 * 1000L

@Singleton
class EmbyStreamSource @Inject constructor(
    @ApplicationContext context: Context,
    private val tmdbService: TmdbService,
) {
    private val client = EmbyClient.forDevice(context)
    private val sessionStore = EmbySessionStore(context.applicationContext)
    private val matchCache = ConcurrentHashMap<String, Pair<Long, List<EmbyItem>>>()
    private val episodeCache = ConcurrentHashMap<String, Pair<Long, List<EmbyItem>>>()

    val sourceName: String get() = SERVER_NAME

    fun hasSession(): Boolean = sessionStore.hasSession()

    suspend fun streamsFor(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        lookupTitle: String? = null,
        lookupYear: Int? = null,
        forceRefresh: Boolean = false,
    ): List<AddonStreams> {
        val session = sessionStore.load() ?: return emptyList()
        val normalizedType = when (type.lowercase()) {
            "series", "tv", "show" -> "series"
            "movie" -> "movie"
            else -> return emptyList()
        }

        val baseId = embyBaseContentId(videoId, normalizedType)
        val normalizedTitle = lookupTitle?.trim()?.takeIf(String::isNotBlank)
        val cacheKey = "${session.userId}|$normalizedType|$baseId|${normalizedTitle.orEmpty().lowercase()}|${lookupYear ?: 0}"
        val matched = (if (forceRefresh) null else cached(matchCache, cacheKey)) ?: run {
            val directImdb = baseId.takeIf { it.startsWith("tt", ignoreCase = true) }
            val directTmdb = baseId.removePrefix("tmdb:").takeIf { value ->
                value.isNotBlank() && value.all(Char::isDigit)
            }

            // Query the identity already supplied by the catalog first. For private catalog IDs,
            // this goes straight to the bounded title search without touching TMDB at all.
            var found = runCatching {
                client.findMatchingItems(
                    session = session,
                    type = normalizedType,
                    tmdbId = directTmdb,
                    imdbId = directImdb,
                    title = normalizedTitle,
                    year = lookupYear,
                )
            }.onFailure { Log.w(TAG, "Omega match failed for $videoId: ${it.message}") }
                .getOrDefault(emptyList())

            // Only pay for an external ID conversion if the catalog identity and title both miss.
            if (found.isEmpty() && directImdb != null) {
                val mappedTmdb = runCatching {
                    tmdbService.ensureTmdbId(directImdb, normalizedType)
                }.getOrNull()
                if (!mappedTmdb.isNullOrBlank()) {
                    found = runCatching {
                        client.findMatchingItems(
                            session = session,
                            type = normalizedType,
                            tmdbId = mappedTmdb,
                            imdbId = null,
                            title = normalizedTitle,
                            year = lookupYear,
                        )
                    }.getOrDefault(emptyList())
                }
            } else if (found.isEmpty() && directTmdb != null) {
                val fallbackImdb = runCatching {
                    tmdbService.tmdbToImdb(directTmdb.toInt(), normalizedType)
                }.getOrNull()
                if (!fallbackImdb.isNullOrBlank()) {
                    found = runCatching {
                        client.findMatchingItems(
                            session = session,
                            type = normalizedType,
                            tmdbId = null,
                            imdbId = fallbackImdb,
                            title = normalizedTitle,
                            year = lookupYear,
                        )
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
                val episodes = (if (forceRefresh) null else cached(episodeCache, key)) ?: run {
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

        // PlaybackInfo calls are independent. Resolve the few available editions in parallel
        // so a multi-version movie does not make a TV user wait on serial network round trips.
        val streams = coroutineScope {
            playableItems.distinctBy { it.id }.take(4).map { item ->
                async {
                    val source = runCatching { client.playbackSource(session, item) }
                        .onFailure { Log.w(TAG, "omega playback resolve failed for ${item.name}: ${it.message}") }
                        .getOrNull() ?: return@async null

                    val fileName = source.fileName?.takeIf { it.isNotBlank() } ?: item.name
                    val sizeLabel = source.fileSizeBytes?.let(::formatFileSize)?.takeIf(String::isNotBlank)
                    val details = listOfNotNull(sizeLabel, fileName).joinToString(" • ")

                    Stream(
                        // StreamCard prioritizes `name`, so put the useful file details here
                        // instead of the old generic "Direct Play Emby"/server label.
                        name = details.ifBlank { fileName },
                        title = fileName,
                        description = sizeLabel,
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
                            videoSize = source.fileSizeBytes,
                            filename = fileName,
                        ),
                        addonName = SERVER_NAME,
                        addonLogo = null,
                        sources = listOf(SERVER_NAME),
                    )
                }
            }.awaitAll().filterNotNull()
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

/** Keep namespaced catalog IDs intact; only strip the numeric S:E suffix used for episodes. */
internal fun embyBaseContentId(videoId: String, normalizedType: String): String {
    val clean = videoId.substringBefore('/').trim()
    if (normalizedType != "series") return clean
    val parts = clean.split(':')
    val hasEpisodeSuffix = parts.size >= 3 &&
        parts[parts.lastIndex].toIntOrNull() != null &&
        parts[parts.lastIndex - 1].toIntOrNull() != null
    return if (hasEpisodeSuffix) parts.dropLast(2).joinToString(":") else clean
}
