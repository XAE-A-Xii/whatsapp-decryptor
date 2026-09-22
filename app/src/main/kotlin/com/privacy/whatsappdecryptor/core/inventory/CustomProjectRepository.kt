package com.privacy.whatsappdecryptor.core.inventory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CustomProjectRepository {

    private const val FILE_NAME = "custom_projects.json"

    fun loadAndSync(dir: File): List<ProjectRegistry.CustomProject> {
        val file = File(dir, FILE_NAME)
        if (!file.exists()) {
            ProjectRegistry.setCustomProjects(emptyList())
            return emptyList()
        }
        val list = mutableListOf<ProjectRegistry.CustomProject>()
        try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                val aliasesArray = obj.optJSONArray("aliases") ?: JSONArray()
                val aliases = mutableListOf<String>()
                for (j in 0 until aliasesArray.length()) {
                    val a = aliasesArray.optString(j, "").trim()
                    if (a.isNotBlank()) aliases.add(a)
                }
                if (name.isNotBlank()) {
                    list.add(ProjectRegistry.CustomProject(canonicalName = name, aliases = aliases))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ProjectRegistry.setCustomProjects(list)
        return list
    }

    fun loadAndSync(context: Context): List<ProjectRegistry.CustomProject> =
        loadAndSync(context.noBackupFilesDir)

    fun addCustomProject(dir: File, name: String, aliases: List<String> = emptyList()): Boolean {
        val cleanName = name.trim().uppercase()
        if (cleanName.isBlank()) return false
        val current = loadAndSync(dir).toMutableList()
        current.removeAll { it.canonicalName.equals(cleanName, ignoreCase = true) }
        current.add(ProjectRegistry.CustomProject(cleanName, aliases.map { it.trim() }.filter { it.isNotBlank() }))
        save(dir, current)
        ProjectRegistry.setCustomProjects(current)
        return true
    }

    fun addCustomProject(context: Context, name: String, aliases: List<String> = emptyList()): Boolean =
        addCustomProject(context.noBackupFilesDir, name, aliases)

    fun removeCustomProject(dir: File, name: String): Boolean {
        val cleanName = name.trim().uppercase()
        val current = loadAndSync(dir).toMutableList()
        val removed = current.removeAll { it.canonicalName.equals(cleanName, ignoreCase = true) }
        if (removed) {
            save(dir, current)
            ProjectRegistry.setCustomProjects(current)
        }
        return removed
    }

    fun removeCustomProject(context: Context, name: String): Boolean =
        removeCustomProject(context.noBackupFilesDir, name)

    private fun save(dir: File, list: List<ProjectRegistry.CustomProject>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("name", item.canonicalName)
                val aliasesArray = JSONArray()
                item.aliases.forEach { aliasesArray.put(it) }
                obj.put("aliases", aliasesArray)
                jsonArray.put(obj)
            }
            val file = File(dir, FILE_NAME)
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
