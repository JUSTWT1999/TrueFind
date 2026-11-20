package com.truefind.shared.data

import com.truefind.shared.model.DefaultPromptTemplates
import com.truefind.shared.model.PromptTemplate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

class PromptTemplateRepository(private val store: KeyValueStore) {

    fun getAll(): List<PromptTemplate> {
        val raw = store.getString(KEY_TEMPLATES) ?: return DefaultPromptTemplates.ALL
        return runCatching { decode(raw) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: DefaultPromptTemplates.ALL
    }

    fun upsert(title: String, body: String, existingId: String? = null): List<PromptTemplate> {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        require(cleanTitle.isNotEmpty() && cleanBody.isNotEmpty()) {
            "Template title and body must not be blank"
        }

        val current = getAll().toMutableList()
        if (existingId != null) {
            val idx = current.indexOfFirst { it.id == existingId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(title = cleanTitle, body = cleanBody)
            } else {
                current.add(PromptTemplate(id = existingId, title = cleanTitle, body = cleanBody))
            }
        } else {
            val suffix = Random.nextLong().toULong().toString(16)
            current.add(
                PromptTemplate(
                    id = "custom_$suffix",
                    title = cleanTitle,
                    body = cleanBody,
                    isBuiltIn = false
                )
            )
        }
        save(current)
        return current
    }

    fun delete(id: String): List<PromptTemplate> {
        val current = getAll()
        if (current.size <= 1) return current
        val updated = current.filterNot { it.id == id }
        val finalList = updated.ifEmpty { DefaultPromptTemplates.ALL }
        save(finalList)
        return finalList
    }

    fun resetToDefaults(): List<PromptTemplate> {
        store.putString(KEY_TEMPLATES, null)
        return DefaultPromptTemplates.ALL
    }

    private fun save(templates: List<PromptTemplate>) {
        store.putString(KEY_TEMPLATES, encode(templates))
    }

    companion object {
        private const val KEY_TEMPLATES = "prompt_templates_json"
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(templates: List<PromptTemplate>): String =
            json.encodeToString(templates)

        fun decode(rawJson: String): List<PromptTemplate> =
            json.decodeFromString<List<PromptTemplate>>(rawJson)
                .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.body.isNotBlank() }
    }
}
