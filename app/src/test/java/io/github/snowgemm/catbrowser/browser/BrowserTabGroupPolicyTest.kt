package io.github.snowgemm.catbrowser.browser

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserTabGroupPolicyTest {
    @Test
    fun normalizeNameTrimsAndCollapsesWhitespace() {
        assertEquals(
            "Research notes",
            BrowserTabGroupPolicy.normalizeName("  Research   notes  "),
        )
    }

    @Test
    fun normalizeNameRespectsMaximumLength() {
        val normalized = BrowserTabGroupPolicy.normalizeName("a".repeat(60))

        assertEquals(
            BrowserTabGroupPolicy.MAX_NAME_LENGTH,
            normalized.length,
        )
    }

    @Test
    fun normalizeColorIndexClampsToPaletteBounds() {
        assertEquals(
            0,
            BrowserTabGroupPolicy.normalizeColorIndex(-1),
        )
        assertEquals(
            BrowserTabGroupPolicy.COLOR_COUNT - 1,
            BrowserTabGroupPolicy.normalizeColorIndex(BrowserTabGroupPolicy.COLOR_COUNT),
        )
    }

    @Test
    fun privateTabsNeverRetainGroupMembership() {
        val tab =
            BrowserTab(
            isPrivate = true,
            initialGroupId = "group-private",
        )

        assertNull(tab.groupId)
    }

    @Test
    fun regularTabsCanRetainGroupMembership() {
        val tab = BrowserTab(
            isPrivate = false,
            initialGroupId = "group-regular",
        )

        assertEquals("group-regular", tab.groupId)
    }

    @Test
    fun localStoreRestoresRegularGroupsWithoutPersistingPrivateTabs() {
        val preferences = InMemorySharedPreferences()
        val store = BrowserLocalStore(preferences)
        val group =
            BrowserTabGroup(
            id = "group-regular",
            name = "Research",
            colorIndex = 2,
        )
        val regularTab =
            BrowserTab(
            id = "regular-tab",
            initialUrl = "https://example.com/",
            initialTitle = "Example",
            initialGroupId = group.id,
        )
        val privateTab =
            BrowserTab(
            id = "private-tab",
            initialUrl = "https://private.example/",
            initialTitle = "Private page",
            isPrivate = true,
            initialGroupId = group.id,
        )

        store.saveTabs(
            tabs = listOf(regularTab, privateTab),
            selectedTabId = privateTab.id,
            defaultUrl = "https://example.org/",
            groups = listOf(group),
        )

        val restored = store.restoreTabs(
            defaultUrl = "https://example.org/",
            maxTabs = 20,
        )

        assertEquals(1, restored.tabs.size)
        assertEquals("regular-tab", restored.tabs.single().id)
        assertEquals(group.id, restored.tabs.single().groupId)
        assertEquals(listOf(group), restored.groups)
        assertEquals("regular-tab", restored.selectedTabId)
        assertFalse(
            preferences.getString("tabs", "").orEmpty().contains("private.example"),
        )
        assertFalse(
            preferences.getString("tab_groups", "").orEmpty().contains("private"),
        )
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? =
        (values[key] as? Set<*>)
            ?.filterIsInstance<String>()
            ?.toMutableSet()
            ?: defValues?.toMutableSet()
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = MemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private var shouldClear = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            updates[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            updates[key] = values?.toSet()
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            updates[key] = value
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            updates[key] = value
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            updates[key] = value
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            updates[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            updates[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            shouldClear = true
            return this
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (shouldClear) values.clear()
            updates.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
        }
    }
}
