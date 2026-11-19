package io.github.snowgemm.catbrowser.update

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUpdateArtifactsTest {
    @Test
    fun successfulInstallCleanupRemovesApksAndPartialFilesOnly() {
        val cacheDir = Files.createTempDirectory("cat-update-cleanup").toFile()
        try {
            val stableApk = cacheDir.resolve("cat-browser-stable-update.apk")
            val betaApk = cacheDir.resolve("cat-browser-beta-update.apk")
            val stablePart = cacheDir.resolve("cat-browser-stable-update.apk.part")
            val unrelated = cacheDir.resolve("web-cache.bin")
            listOf(stableApk, betaApk, stablePart, unrelated).forEach {
                assertTrue(it.createNewFile())
            }

            assertEquals(3, BrowserUpdateArtifacts.removeInstalledApks(cacheDir))

            assertFalse(stableApk.exists())
            assertFalse(betaApk.exists())
            assertFalse(stablePart.exists())
            assertTrue(unrelated.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun startupCleanupRemovesOnlyAbandonedPartialDownloads() {
        val cacheDir = Files.createTempDirectory("cat-update-partial").toFile()
        try {
            val partial = cacheDir.resolve("cat-browser-beta-update.apk.part")
            val completed = cacheDir.resolve("cat-browser-beta-update.apk")
            assertTrue(partial.createNewFile())
            assertTrue(completed.createNewFile())

            assertEquals(1, BrowserUpdateArtifacts.removePartialDownloads(cacheDir))

            assertFalse(partial.exists())
            assertTrue(completed.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
