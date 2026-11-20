package com.truefind.app.data

import com.truefind.app.model.DefaultPromptTemplates
import com.truefind.app.model.PromptTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
            current.add(
                PromptTemplate(
                    id = "custom_${UUID.randomUUID()}",
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

        fun encode(templates: List<PromptTemplate>): String {
            val arr = JSONArray()
            for (tpl in templates) {
                arr.put(
                    JSONObject().apply {
                        put("id", tpl.id)
                        put("title", tpl.title)
                        put("body", tpl.body)
                        put("isBuiltIn", tpl.isBuiltIn)
                    }
                )
            }
            return arr.toString()
        }

        fun decode(rawJson: String): List<PromptTemplate> {
            val arr = JSONArray(rawJson)
            val result = ArrayList<PromptTemplate>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id").trim()
                val title = obj.optString("title").trim()
                val body = obj.optString("body").trim()
                if (id.isNotEmpty() && title.isNotEmpty() && body.isNotEmpty()) {
                    result.add(
                        PromptTemplate(
                            id = id,
                            title = title,
                            body = body,
                            isBuiltIn = obj.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
            return result
        }
    }
}
