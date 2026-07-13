package com.reelshort.app.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {
    @Test
    fun parsesVersionNameAndReleaseTag() {
        assertEquals(SemanticVersion(0, 2, 0), SemanticVersion.parse("0.2.0"))
        assertEquals(SemanticVersion(12, 34, 56), SemanticVersion.parse("v12.34.56"))
    }

    @Test
    fun rejectsMalformedOrPreReleaseVersions() {
        listOf("", "v1", "1.2", "v1.2.3-beta", "01.2.3", "1.02.3", "1.2.03", "1.2.3.4")
            .forEach { assertNull(SemanticVersion.parse(it), it) }
    }

    @Test
    fun comparesMajorMinorAndPatchComponents() {
        assertTrue(SemanticVersion(1, 0, 0) > SemanticVersion(0, 99, 99))
        assertTrue(SemanticVersion(1, 2, 0) > SemanticVersion(1, 1, 99))
        assertTrue(SemanticVersion(1, 2, 4) > SemanticVersion(1, 2, 3))
        assertEquals(0, SemanticVersion(1, 2, 3).compareTo(SemanticVersion(1, 2, 3)))
    }
}
