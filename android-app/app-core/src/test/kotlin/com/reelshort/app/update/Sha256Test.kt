package com.reelshort.app.update

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun hashesFileWithoutLoadingContractChanges() {
        val file = Files.createTempFile("shortlink", ".apk").toFile().apply { writeText("abc") }

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256(file),
        )
    }
}
