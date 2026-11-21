package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRequestGenerationTest {
    @Test
    fun newestRequestInvalidatesOlderResults() {
        val requests = BrowserRequestGeneration()

        val first = requests.next()
        val second = requests.next()

        assertFalse(requests.isCurrent(first))
        assertTrue(requests.isCurrent(second))
    }

    @Test
    fun clearingTheCurrentRequestRejectsItsLateCallback() {
        val requests = BrowserRequestGeneration()
        val request = requests.next()

        requests.invalidate()

        assertFalse(requests.isCurrent(request))
    }
}
