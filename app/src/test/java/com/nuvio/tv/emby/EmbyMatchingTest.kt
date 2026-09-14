package com.nuvio.tv.emby

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmbyMatchingTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `private catalog id falls back to bounded title and year search`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"Items":[
                    {"Id":"old","Name":"Spider Man: Brand New Day","Type":"Movie","ProductionYear":2025},
                    {"Id":"match","Name":"Spider-Man: Brand New Day","Type":"Movie","ProductionYear":2026}
                ]}""".trimIndent()
            ).build()
        )
        val client = EmbyClient(
            http = OkHttpClient(),
            deviceId = "test-device",
            appVersion = "test",
            deviceDisplayName = "Test TV",
        )
        val session = EmbySession(
            serverUrl = server.url("/").toString().trimEnd('/'),
            userId = "user",
            accessToken = "token",
            username = "tester",
        )

        val result = client.findMatchingItems(
            session = session,
            type = "movie",
            tmdbId = null,
            imdbId = null,
            title = "Spider-Man: Brand New Day",
            year = 2026,
        )

        assertEquals(listOf("match"), result.map { it.id })
        val request = server.takeRequest()
        assertEquals("Spider-Man: Brand New Day", request.url.queryParameter("SearchTerm"))
        assertEquals("12", request.url.queryParameter("Limit"))
        assertEquals("Movie", request.url.queryParameter("IncludeItemTypes"))
    }

    @Test
    fun `namespaced ids stay intact while episode suffix is removed`() {
        assertEquals("tmdb:12345", embyBaseContentId("tmdb:12345", "movie"))
        assertEquals("xperience:show-42", embyBaseContentId("xperience:show-42:2:7", "series"))
        assertEquals("tt1234567", embyBaseContentId("tt1234567:2:7", "series"))
        assertTrue(normalizeEmbyTitle("Spider-Man: Brand New Day") == "spidermanbrandnewday")
    }
}
