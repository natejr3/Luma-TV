package com.nuvio.tv.emby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EmbyClient(private val http: OkHttpClient = OkHttpClient()) {
    private val deviceId = "luma-tv-${UUID.randomUUID()}"

    suspend fun authenticate(server: String, username: String, password: String): EmbySession {
        val normalized = server.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }
        val body = JSONObject()
            .put("Username", username)
            .put("Pw", password)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$normalized/Users/AuthenticateByName")
            .header("X-Emby-Authorization", authorization())
            .post(body)
            .build()
        val json = executeJson(request)
        val user = json.getJSONObject("User")
        return EmbySession(
            serverUrl = normalized,
            userId = user.getString("Id"),
            accessToken = json.getString("AccessToken"),
            username = user.optString("Name", username),
        )
    }

    suspend fun loadHome(session: EmbySession): EmbyHomeCatalog = coroutineScope {
        val rows = listOf(
            async { "Continue Watching" to getItems(session, "/Users/${session.userId}/Items/Resume", mapOf("Limit" to "24", "Fields" to commonFields)) },
            async { "Next Up" to getItems(session, "/Shows/NextUp", mapOf("UserId" to session.userId, "Limit" to "24", "Fields" to commonFields)) },
            async { "Recently Added" to getItems(session, "/Users/${session.userId}/Items/Latest", mapOf("Limit" to "24", "IncludeItemTypes" to "Movie,Episode", "Fields" to commonFields)) },
            async { "Popular on Your Server" to getItems(session, "/Users/${session.userId}/Items", common("Movie,Series") + mapOf("SortBy" to "PlayCount", "SortOrder" to "Descending", "Limit" to "24")) },
            async { "Movies" to getItems(session, "/Users/${session.userId}/Items", common("Movie") + mapOf("SortBy" to "DateCreated", "SortOrder" to "Descending", "Limit" to "30")) },
            async { "TV Shows" to getItems(session, "/Users/${session.userId}/Items", common("Series") + mapOf("SortBy" to "DateCreated", "SortOrder" to "Descending", "Limit" to "30")) },
            async { "Favorites" to getItems(session, "/Users/${session.userId}/Items", common("Movie,Series") + mapOf("Filters" to "IsFavorite", "Limit" to "30")) },
        ).awaitAll().mapNotNull { (title, items) ->
            items.takeIf { it.isNotEmpty() }?.let { EmbyRow(title, it) }
        }
        EmbyHomeCatalog(hero = rows.firstOrNull()?.items?.firstOrNull(), rows = rows)
    }

    suspend fun search(session: EmbySession, query: String): List<EmbyItem> {
        if (query.isBlank()) return emptyList()
        return getItems(
            session,
            "/Users/${session.userId}/Items",
            common("Movie,Series,Episode") + mapOf("SearchTerm" to query, "Limit" to "60"),
        )
    }

    suspend fun loadSeriesEpisodes(session: EmbySession, seriesId: String): List<EmbyItem> =
        getItems(
            session,
            "/Users/${session.userId}/Items",
            common("Episode") + mapOf(
                "ParentId" to seriesId,
                "SortBy" to "ParentIndexNumber,IndexNumber,SortName",
                "SortOrder" to "Ascending",
                "Limit" to "500",
            ),
        )

    suspend fun findMatchingItems(
        session: EmbySession,
        type: String,
        tmdbId: String?,
        imdbId: String?,
    ): List<EmbyItem> {
        val includeType = if (type.equals("series", true) || type.equals("tv", true)) "Series" else "Movie"
        val providerKeys = buildList {
            tmdbId?.takeIf { it.isNotBlank() }?.let { add("tmdb.$it") }
            imdbId?.takeIf { it.isNotBlank() }?.let { add("imdb.$it") }
        }
        val matched = mutableListOf<EmbyItem>()
        for (providerKey in providerKeys) {
            val items = runCatching {
                getItems(
                    session,
                    "/Users/${session.userId}/Items",
                    common(includeType) + mapOf(
                        "AnyProviderIdEquals" to providerKey,
                        "Limit" to "20",
                    ),
                )
            }.getOrDefault(emptyList())
            matched += items
            if (matched.isNotEmpty()) break
        }
        return matched.distinctBy { it.id }
    }

    fun imageUrl(session: EmbySession, item: EmbyItem, backdrop: Boolean = false): String? {
        val tag = if (backdrop) item.backdropTag else item.imageTag
        if (tag == null) return null
        val type = if (backdrop) "Backdrop" else "Primary"
        return "${session.serverUrl}/Items/${item.id}/Images/$type?tag=$tag&maxWidth=${if (backdrop) 1920 else 600}&quality=88&api_key=${session.accessToken}"
    }

    suspend fun playbackSource(session: EmbySession, requestedItem: EmbyItem): EmbyPlaybackSource = withContext(Dispatchers.IO) {
        val item = if (requestedItem.isSeries) {
            getItems(
                session,
                "/Shows/NextUp",
                mapOf("UserId" to session.userId, "SeriesId" to requestedItem.id, "Limit" to "1", "Fields" to commonFields),
            ).firstOrNull() ?: loadSeriesEpisodes(session, requestedItem.id).firstOrNull()
            ?: error("No playable episodes were found for this series.")
        } else requestedItem

        val playbackInfoUrl = (session.serverUrl + "/Items/${item.id}/PlaybackInfo").toHttpUrl().newBuilder()
            .addQueryParameter("UserId", session.userId)
            .addQueryParameter("IsPlayback", "true")
            .addQueryParameter("AutoOpenLiveStream", "true")
            .addQueryParameter("api_key", session.accessToken)
            .build()
        val request = Request.Builder()
            .url(playbackInfoUrl)
            .header("X-Emby-Token", session.accessToken)
            .header("X-Emby-Authorization", authorization(session.accessToken))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val json = executeJson(request)
        val source = json.optJSONArray("MediaSources")?.optJSONObject(0)
        val headers = mapOf(
            "X-Emby-Token" to session.accessToken,
            "X-Emby-Authorization" to authorization(session.accessToken),
        )
        if (source == null) {
            return@withContext EmbyPlaybackSource(
                staticStreamUrl(session, item), headers, item, true, fileName = item.name
            )
        }

        val mediaSourceId = source.optString("Id")
        val direct = source.optBoolean("SupportsDirectPlay", true)
        val transcodingUrl = source.optString("TranscodingUrl").takeIf { it.isNotBlank() }
        val fileName = sourceFileName(source, item)
        val fileSizeBytes = source.optLong("Size").takeIf { it > 0L }

        when {
            direct -> EmbyPlaybackSource(
                uri = (session.serverUrl + "/Videos/${item.id}/stream").toHttpUrl().newBuilder()
                    .addQueryParameter("Static", "true")
                    .addQueryParameter("MediaSourceId", mediaSourceId)
                    .addQueryParameter("api_key", session.accessToken)
                    .build().toString(),
                headers = headers,
                item = item,
                isDirectPlay = true,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
            )
            transcodingUrl != null -> EmbyPlaybackSource(
                uri = if (transcodingUrl.startsWith("http")) transcodingUrl else session.serverUrl + transcodingUrl,
                headers = headers,
                item = item,
                isDirectPlay = false,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
            )
            else -> EmbyPlaybackSource(
                staticStreamUrl(session, item), headers, item, true, fileName, fileSizeBytes
            )
        }
    }

    private fun sourceFileName(source: JSONObject, item: EmbyItem): String {
        val path = source.optString("Path").takeIf { it.isNotBlank() }
        val pathName = path
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
        return pathName
            ?: source.optString("Name").takeIf { it.isNotBlank() }
            ?: item.name
    }

    private fun staticStreamUrl(session: EmbySession, item: EmbyItem): String =
        "${session.serverUrl}/Videos/${item.id}/stream?Static=true&api_key=${session.accessToken}"

    private val commonFields = "Overview,Genres,CommunityRating,OfficialRating,RunTimeTicks,PrimaryImageAspectRatio,DateCreated,UserData,SeriesName,SeasonName,IndexNumber,ParentIndexNumber"

    private fun common(types: String) = mapOf(
        "Recursive" to "true",
        "IncludeItemTypes" to types,
        "Fields" to commonFields,
        "EnableImages" to "true",
    )

    private suspend fun getItems(session: EmbySession, path: String, query: Map<String, String>): List<EmbyItem> = withContext(Dispatchers.IO) {
        val url = (session.serverUrl + path).toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", session.accessToken)
            .header("X-Emby-Authorization", authorization(session.accessToken))
            .build()
        val content = execute(request)
        val array = if (content.trimStart().startsWith("[")) {
            JSONArray(content)
        } else {
            val json = JSONObject(content)
            json.optJSONArray("Items") ?: json.optJSONArray("items") ?: return@withContext emptyList()
        }
        buildList {
            for (index in 0 until array.length()) add(parseItem(array.getJSONObject(index)))
        }
    }

    private fun parseItem(json: JSONObject): EmbyItem {
        val imageTags = json.optJSONObject("ImageTags")
        val backdrops = json.optJSONArray("BackdropImageTags")
        val userData = json.optJSONObject("UserData")
        val genresJson = json.optJSONArray("Genres")
        val genres = buildList {
            if (genresJson != null) for (i in 0 until genresJson.length()) {
                genresJson.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return EmbyItem(
            id = json.getString("Id"),
            name = json.optString("Name", "Untitled"),
            type = json.optString("Type", "Video"),
            overview = json.optString("Overview"),
            year = json.optInt("ProductionYear").takeIf { it > 0 },
            communityRating = json.optDouble("CommunityRating").takeUnless { it.isNaN() || it == 0.0 },
            officialRating = json.optString("OfficialRating").takeIf { it.isNotBlank() },
            runTimeTicks = json.optLong("RunTimeTicks").takeIf { it > 0 },
            genres = genres,
            imageTag = imageTags?.optString("Primary")?.takeIf { it.isNotBlank() },
            backdropTag = backdrops?.optString(0)?.takeIf { it.isNotBlank() },
            playedPercentage = userData?.optDouble("PlayedPercentage")?.takeUnless { it.isNaN() },
            isFavorite = userData?.optBoolean("IsFavorite") ?: false,
            seriesName = json.optString("SeriesName").takeIf { it.isNotBlank() },
            seasonName = json.optString("SeasonName").takeIf { it.isNotBlank() },
            indexNumber = json.optInt("IndexNumber").takeIf { it > 0 },
            parentIndexNumber = json.optInt("ParentIndexNumber").takeIf { it > 0 },
        )
    }

    private suspend fun executeJson(request: Request): JSONObject = JSONObject(execute(request))

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val content = it.body?.string().orEmpty()
                    if (!continuation.isActive) return
                    if (!it.isSuccessful) {
                        val message = runCatching { JSONObject(content).optString("Message") }.getOrNull()
                        continuation.resumeWithException(
                            IOException(message?.takeIf(String::isNotBlank) ?: "Emby returned ${it.code}")
                        )
                    } else {
                        continuation.resume(content)
                    }
                }
            }
        })
    }

    private fun authorization(token: String? = null): String = buildString {
        append("MediaBrowser Client=\"Luma TV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"1.0\"")
        if (token != null) append(", Token=\"$token\"")
    }
}
