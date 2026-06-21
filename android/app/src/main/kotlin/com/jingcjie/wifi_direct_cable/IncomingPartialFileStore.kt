package com.jingcjie.wifi_direct_cable

import java.io.File
import java.io.IOException
import java.util.UUID

internal class IncomingPartialFileStore(
    private val directory: File
) {
    fun create(transferId: String): File {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create incoming transfer cache")
        }
        val safeId = UUID.nameUUIDFromBytes(transferId.toByteArray(Charsets.UTF_8))
        return File(directory, "$safeId-${UUID.randomUUID()}.wdcable-part")
    }

    fun cleanupStaleFiles(): Int {
        var deleted = 0
        directory.listFiles()?.forEach { file ->
            if (
                file.isFile &&
                file.name.endsWith(".wdcable-part") &&
                file.delete()
            ) {
                deleted++
            }
        }
        return deleted
    }
}
