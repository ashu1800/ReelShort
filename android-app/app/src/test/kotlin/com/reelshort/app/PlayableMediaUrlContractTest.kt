package com.reelshort.app
import com.reelshort.app.ui.format.playableMediaUrlOrNull

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayableMediaUrlContractTest {
    @Test
    fun blankMediaUrlIsNotPlayable() {
        assertNull(null.playableMediaUrlOrNull())
        assertNull("".playableMediaUrlOrNull())
        assertNull("   ".playableMediaUrlOrNull())
    }

    @Test
    fun httpAndHttpsMediaUrlsArePlayableAfterTrim() {
        assertEquals(
            "https://media.example.com/book/1.m3u8",
            "  https://media.example.com/book/1.m3u8  ".playableMediaUrlOrNull(),
        )
        assertEquals(
            "http://10.0.2.2:8080/video/1.m3u8",
            "http://10.0.2.2:8080/video/1.m3u8".playableMediaUrlOrNull(),
        )
    }

    @Test
    fun nonHttpMediaUrlsAreNotPlayable() {
        assertNull("file:///sdcard/movie.mp4".playableMediaUrlOrNull())
        assertNull("content://media/movie".playableMediaUrlOrNull())
        assertNull("javascript:alert(1)".playableMediaUrlOrNull())
    }
}
