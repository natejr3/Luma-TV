package com.lumatv.app.data

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
import org.json.JSONObject
import org.json.JSONArray
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
        val body = JSONObject().put("Username", username).put("Pw", password).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$normalized/Users/AuthenticateByName")
            .header("X-Emby-Authorization", authorization())
            .post(body)
            .build()
        val json = executeJson(request)
        val user = json.getJSONObject("User")
        return EmbySession(normalized, user.getString("Id"), json.getString("AccessToken"), user.getString("Name"))
    }

    suspend fun loadHome(session: EmbySession): HomeCatalog = coroutineScope {
        val requests = listOf(
            async { "Continue Watching" to getItems(session, "/Users/${session.userId}/Items/Resume", mapOf("Limit" to "20")) },
            async { "Recently Added" to getItems(session, "/Users/${session.userId}/Items/Latest", mapOf("Limit" to "20", "IncludeItemTypes" to "Movie,Episode")) },
            async { "Trending on Your Server" to getItems(session, "/Users/${session.userId}/Items", common("Movie,Series") + mapOf("SortBy" to "PlayCount", "SortOrder" to "Descending", "Limit" to "20")) },
            async { "Movies" to getItems(session, "/Users/${session.userId}/Items", common("Movie") + mapOf("SortBy" to "DateCreated", "SortOrder" to "Descending", "Limit" to "30")) },
            async { "TV Shows" to getItems(session, "/Users/${session.userId}/Items", common("Series") + mapOf("SortBy" to "DateCreated", "SortOrder" to "Descending", "Limit" to "30")) },
            async { "My Favorites" to getItems(session, "/Users/${session.userId}/Items", common("Movie,Series") + mapOf("Filters" to "IsFavorite", "Limit" to "30")) },
        ).awaitAll()
        val coreRows = requests.mapNotNull { (title, items) -> items.takeIf { it.isNotEmpty() }?.let { CatalogRow(title, it) } }
        val libraryItems = requests.filter { it.first == "Movies" || it.first == "TV Shows" }.flatMap { it.second }
        val genreRows = libraryItems.flatMap { it.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(6)
            .mapNotNull { (genre, _) ->
                libraryItems.filter { genre in it.genres }.take(20).takeIf { it.size >= 3 }?.let { CatalogRow(genre, it) }
            }
        val rows = coreRows + genreRows
        HomeCatalog(hero = rows.firstOrNull()?.items?.firstOrNull(), rows = rows)
    }

    suspend fun search(session: EmbySession, query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        return getItems(
            session,
            "/Users/${session.userId}/Items",
            common("Movie,Series,Episode") + mapOf("SearchTerm" to query, "Limit" to "60"),
        )
    }

    fun imageUrl(session: EmbySession, item: MediaItem, backdrop: Boolean = false): String? {
        val tag = if (backdrop) item.backdropTag else item.imageTag
        if (tag == null) return null
        val type = if (backdrop) "Backdrop" else "Primary"
        return "${session.serverUrl}/Items/${item.id}/Images/$type?tag=$tag&maxWidth=${if (backdrop) 1920 else 600}&quality=88&api_key=${session.accessToken}"
    }

    fun streamUrl(session: EmbySession, item: MediaItem): String =
        "${session.serverUrl}/Videos/${item.id}/stream?Static=true&api_key=${session.accessToken}"

    suspend fun playbackSource(session: EmbySession, item: MediaItem): PlaybackSource = withContext(Dispatchers.IO) {
        val playable = if (item.type.equals("Series", true)) {
            getItems(
                session,
                "/Shows/NextUp",
                mapOf("UserId" to session.userId, "SeriesId" to item.id, "Limit" to "1", "Fields" to commonFields),
            ).firstOrNull() ?: getItems(
                session,
                "/Users/${session.userId}/Items",
                common("Episode") + mapOf("ParentId" to item.id, "SortBy" to "SortName", "SortOrder" to "Ascending", "Limit" to "1"),
            ).firstOrNull() ?: error("No playable episodes were found for this series.")
        } else item
        val url = (session.serverUrl + "/Items/${playable.id}/PlaybackInfo").toHttpUrl().newBuilder()
            .addQueryParameter("UserId", session.userId)
            .addQueryParameter("IsPlayback", "true")
            .addQueryParameter("AutoOpenLiveStream", "true")
            .addQueryParameter("api_key", session.accessToken)
            .build()
        val request = Request.Builder().url(url)
            .header("X-Emby-Token", session.accessToken)
            .header("X-Emby-Authorization", authorization(session.accessToken))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val json = executeJson(request)
        val sources = json.optJSONArray("MediaSources")
        val source = sources?.optJSONObject(0)
        if (source == null) return@withContext PlaybackSource(streamUrl(session, playable), true)
        val mediaSourceId = source.optString("Id")
        val direct = source.optBoolean("SupportsDirectPlay", true)
        val transcodingUrl = source.optString("TranscodingUrl").takeIf { it.isNotBlank() }
        when {
            direct -> PlaybackSource(
                (session.serverUrl + "/Videos/${playable.id}/stream").toHttpUrl().newBuilder()
                    .addQueryParameter("Static", "true")
                    .addQueryParameter("MediaSourceId", mediaSourceId)
                    .addQueryParameter("api_key", session.accessToken)
                    .build().toString(),
                true,
            )
            transcodingUrl != null -> PlaybackSource(
                if (transcodingUrl.startsWith("http")) transcodingUrl else session.serverUrl + transcodingUrl,
                false,
            )
            else -> PlaybackSource(streamUrl(session, playable), true)
        }
    }

    private val commonFields = "Overview,Genres,CommunityRating,OfficialRating,RunTimeTicks,PrimaryImageAspectRatio,DateCreated,UserData,SeriesName,SeasonName,IndexNumber"

    private fun common(types: String) = mapOf(
        "Recursive" to "true",
        "IncludeItemTypes" to types,
        "Fields" to commonFields,
        "EnableImages" to "true",
    )

    private suspend fun getItems(session: EmbySession, path: String, query: Map<String, String>): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = (session.serverUrl + path).toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url)
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

    private fun parseItem(json: JSONObject): MediaItem {
        val imageTags = json.optJSONObject("ImageTags")
        val backdrops = json.optJSONArray("BackdropImageTags")
        val userData = json.optJSONObject("UserData")
        val genresJson = json.optJSONArray("Genres")
        val genres = buildList {
            if (genresJson != null) for (i in 0 until genresJson.length()) add(genresJson.optString(i))
        }
        return MediaItem(
            id = json.getString("Id"),
            name = json.optString("Name", "Untitled"),
            type = json.optString("Type", "Video"),
            overview = json.optString("Overview"),
            year = json.optInt("ProductionYear").takeIf { it > 0 },
            communityRating = json.optDouble("CommunityRating").takeUnless { it.isNaN() },
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
                    if (!it.isSuccessful) {
                        val message = runCatching { JSONObject(content).optString("Message") }.getOrNull()
                        continuation.resumeWithException(IOException(message?.takeIf(String::isNotBlank) ?: "Emby returned ${it.code}"))
                    } else continuation.resume(content)
                }
            }
        })
    }

    private fun authorization(token: String? = null): String = buildString {
        append("MediaBrowser Client=\"Luma TV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"0.1.0\"")
        if (token != null) append(", Token=\"$token\"")
    }
}
