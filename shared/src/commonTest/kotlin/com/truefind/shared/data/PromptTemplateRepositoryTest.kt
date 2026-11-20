package com.truefind.shared.data

import com.truefind.shared.model.DefaultPromptTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptTemplateRepositoryTest {

    private class InMemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    @Test
    fun returnsBuiltInDefaultsInitially() {
        val repo = PromptTemplateRepository(InMemoryStore())
        val list = repo.getAll()
        assertEquals(DefaultPromptTemplates.ALL.size, list.size)
        assertEquals("事實查核", list.first().title)
    }

    @Test
    fun supportsCreateEditDeleteAndReset() {
        val repo = PromptTemplateRepository(InMemoryStore())

        val added = repo.upsert("查財經數據", "請驗證以下財報與股價宣稱")
        assertEquals(5, added.size)
        val custom = added.last()

        val edited = repo.upsert("查財經與股市", "請查證財報與新聞來源", existingId = custom.id)
        assertEquals(5, edited.size)
        assertEquals("查財經與股市", edited.last().title)

        val afterDelete = repo.delete(custom.id)
        assertEquals(4, afterDelete.size)
        assertTrue(afterDelete.none { it.id == custom.id })

        val reset = repo.resetToDefaults()
        assertEquals(DefaultPromptTemplates.ALL, reset)
    }

    @Test
    fun neverDeletesLastRemainingTemplate() {
        val repo = PromptTemplateRepository(InMemoryStore())
        var list = repo.getAll()
        for (tpl in list.drop(1)) {
            list = repo.delete(tpl.id)
        }
        assertEquals(1, list.size)
        assertEquals(1, repo.delete(list.first().id).size)
    }
}
