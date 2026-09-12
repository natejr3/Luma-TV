from pathlib import Path

emby = Path('app/src/main/java/com/nuvio/tv/emby/EmbyClient.kt')
text = emby.read_text()
anchor = '    fun imageUrl(session: EmbySession, item: EmbyItem, backdrop: Boolean = false): String? {'
method = '''    suspend fun findMatchingItems(
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

'''
if 'suspend fun findMatchingItems(' not in text:
    if anchor not in text:
        raise SystemExit('EmbyClient anchor not found')
    text = text.replace(anchor, method + anchor, 1)
    emby.write_text(text)

repo = Path('app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt')
text = repo.read_text()
old_ctor = '''    private val xtreamStreamSource: com.nuvio.tv.core.iptv.match.XtreamStreamSource
) : StreamRepository {'''
new_ctor = '''    private val xtreamStreamSource: com.nuvio.tv.core.iptv.match.XtreamStreamSource,
    private val embyStreamSource: com.nuvio.tv.emby.EmbyStreamSource
) : StreamRepository {'''
if 'private val embyStreamSource:' not in text:
    if old_ctor not in text:
        raise SystemExit('StreamRepository constructor anchor not found')
    text = text.replace(old_ctor, new_ctor, 1)

old_jobs = '''                val totalJobs = streamAddons.size +
                    (if (pluginRequest != null) 1 else 0) +
                    xtreamMatchTargets.size'''
new_jobs = '''                val totalJobs = streamAddons.size +
                    (if (pluginRequest != null) 1 else 0) +
                    xtreamMatchTargets.size +
                    1 // Emby library match lane'''
if '1 // Emby library match lane' not in text:
    if old_jobs not in text:
        raise SystemExit('totalJobs anchor not found')
    text = text.replace(old_jobs, new_jobs, 1)

plugin_anchor = '                // Launch plugin jobs if we have a supported plugin id - each scraper sends its own result'
emby_job = '''                // Match normal Home/Discover items against the connected Emby library.
                launch {
                    try {
                        val groups = embyStreamSource.streamsFor(type, videoId, season, episode)
                        groups.forEach { resultChannel.send(it) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Emby stream match failed for $videoId: ${e.message}")
                    } finally {
                        if (completedJobs.incrementAndGet() >= totalJobs) {
                            resultChannel.close()
                        }
                    }
                }

'''
if 'Emby stream match failed for $videoId' not in text:
    if plugin_anchor not in text:
        raise SystemExit('plugin anchor not found')
    text = text.replace(plugin_anchor, emby_job + plugin_anchor, 1)

repo.write_text(text)
