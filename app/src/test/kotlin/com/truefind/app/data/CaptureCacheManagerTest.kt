package com.truefind.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `deletes expired files and enforces maxKeep limit`() {
        val dir = tempFolder.newFolder("captures")
        val now = 10_000_000L
        val ttl = 3_600_000L // 1 hour

        val expired = File(dir, "cap_expired.png").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - ttl - 10_000L)
        }
        val fresh1 = File(dir, "cap_1.png").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - 4_000L)
        }
        val fresh2 = File(dir, "cap_2.png").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - 3_000L)
        }
        val fresh3 = File(dir, "cap_3.png").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - 2_000L)
        }
        val fresh4 = File(dir, "cap_4.png").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - 1_000L)
        }

        val deleted = CaptureCacheManager.cleanupDirectory(dir, nowMs = now, ttlMs = ttl, maxKeep = 3)

        assertEquals(2, deleted)
        assertFalse(expired.exists())
        assertFalse(fresh1.exists())
        assertTrue(fresh2.exists())
        assertTrue(fresh3.exists())
        assertTrue(fresh4.exists())
    }
}
