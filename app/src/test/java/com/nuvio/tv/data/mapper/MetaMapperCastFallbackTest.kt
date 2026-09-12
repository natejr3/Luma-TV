package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaDto
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaMapperCastFallbackTest {

    @Test
    fun `plain addon cast names remain visible without rich extras`() {
        val meta = MetaDto(
            id = "tt1234567",
            type = "movie",
            name = "Movie",
            cast = listOf("Actor One", "Actor Two"),
        ).toDomain()

        assertEquals(listOf("Actor One", "Actor Two"), meta.cast)
        assertEquals(listOf("Actor One", "Actor Two"), meta.castMembers.map { it.name })
    }
}
