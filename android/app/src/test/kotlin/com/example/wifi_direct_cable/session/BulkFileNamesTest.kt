package com.example.wifi_direct_cable.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BulkFileNamesTest {
    @Test
    fun safeFileNameStripsPathSegments() {
        assertEquals("report.txt", BulkFileNames.safeFileName("../nested/report.txt"))
        assertEquals("photo.jpg", BulkFileNames.safeFileName("C:\\Users\\me\\photo.jpg"))
    }

    @Test
    fun safeFileNameReplacesInvalidCharactersAndBlankNames() {
        assertEquals("bad_name_.txt", BulkFileNames.safeFileName("bad:name?.txt"))
        assertEquals("line_break.txt", BulkFileNames.safeFileName("line\nbreak.txt"))
        assertEquals("unknown_file", BulkFileNames.safeFileName("   "))
    }

    @Test
    fun duplicateSafeFileAddsSuffixBeforeExtension() {
        val directory = Files.createTempDirectory("wdcable-file-names").toFile()
        try {
            assertTrue(directory.resolve("sample.txt").createNewFile())
            assertTrue(directory.resolve("sample (1).txt").createNewFile())

            val candidate = BulkFileNames.duplicateSafeFile(directory, "sample.txt")

            assertEquals("sample (2).txt", candidate.name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun duplicateSafeFileHandlesNamesWithoutExtension() {
        val directory = Files.createTempDirectory("wdcable-file-names").toFile()
        try {
            assertTrue(directory.resolve("README").createNewFile())

            val candidate = BulkFileNames.duplicateSafeFile(directory, "README")

            assertEquals("README (1)", candidate.name)
        } finally {
            directory.deleteRecursively()
        }
    }
}
