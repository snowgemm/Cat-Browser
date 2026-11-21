package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionPromptQueueTest {
    @Test
    fun permissionRequestsAreResolvedInArrivalOrder() {
        val queue = ExtensionPromptQueue<String>()

        queue.enqueue("install")
        queue.enqueue("optional")
        queue.enqueue("update")

        assertEquals("install", queue.active)
        assertEquals("optional", queue.resolveActive())
        assertEquals("update", queue.resolveActive())
        assertNull(queue.resolveActive())
    }

    @Test
    fun removingOneExtensionPreservesOtherExtensionsPromptOrder() {
        val queue = ExtensionPromptQueue<Pair<String, String>>()
        queue.enqueue("addon-a" to "first")
        queue.enqueue("addon-b" to "middle")
        queue.enqueue("addon-a" to "last")

        val removed = queue.removeAll { it.first == "addon-a" }

        assertEquals(listOf("addon-a" to "first", "addon-a" to "last"), removed)
        assertEquals("addon-b" to "middle", queue.active)
    }
}
