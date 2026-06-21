package com.jingcjie.wifi_direct_cable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class IncomingPartialFileStoreTest {
    @Test
    fun transferIdCannotEscapePartialDirectory() {
        val directory = Files.createTempDirectory("wdcable-parts").toFile()
        try {
            val file = IncomingPartialFileStore(directory).create("../outside")

            assertEquals(directory.canonicalPath, file.parentFile!!.canonicalPath)
            assertTrue(file.name.endsWith(".wdcable-part"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupDeletesOnlyPartialFiles() {
        val directory = Files.createTempDirectory("wdcable-parts").toFile()
        try {
            val partial = directory.resolve("old.wdcable-part")
            val unrelated = directory.resolve("keep.txt")
            assertTrue(partial.createNewFile())
            assertTrue(unrelated.createNewFile())

            assertEquals(1, IncomingPartialFileStore(directory).cleanupStaleFiles())
            assertFalse(partial.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
