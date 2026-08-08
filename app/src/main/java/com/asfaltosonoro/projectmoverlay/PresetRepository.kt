package com.asfaltosonoro.projectmoverlay

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PresetEntry(val path: String, val name: String, val favorite: Boolean)

/**
 * Gestisce i preset .milk: quelli inclusi nell'app (assets/presets, copiati in
 * filesDir/presets/bundled) e quelli importati dall'utente da una cartella del
 * telefono (copiati in filesDir/presets/imported, perché libprojectM ha bisogno
 * di percorsi file reali e non può leggere direttamente i content:// URI di SAF).
 *
 * Preferiti e playlist sono salvati in filesDir/preset_store.json.
 */
class PresetRepository(private val context: Context) {

    private val bundledDir = File(context.filesDir, "presets/bundled")
    private val importedDir = File(context.filesDir, "presets/imported")
    private val storeFile = File(context.filesDir, "preset_store.json")

    fun ensureBundledPresetsCopied() {
        if (bundledDir.exists() && bundledDir.listFiles()?.isNotEmpty() == true) return
        bundledDir.mkdirs()
        context.assets.list("presets")?.forEach { name ->
            if (!name.endsWith(".milk") && !name.endsWith(".milk2")) return@forEach
            context.assets.open("presets/$name").use { input ->
                File(bundledDir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /** Copia ricorsivamente tutti i .milk/.milk2 trovati sotto la cartella scelta con SAF. */
    fun importFolder(treeUri: Uri, onFinished: (importedCount: Int) -> Unit) {
        Thread {
            var count = 0
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root != null) {
                importedDir.mkdirs()
                count = copyPresetsRecursive(root, importedDir)
            }
            onFinished(count)
        }.start()
    }

    private fun copyPresetsRecursive(dir: DocumentFile, destDir: File): Int {
        var count = 0
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory -> {
                    val sub = File(destDir, doc.name ?: "sub")
                    sub.mkdirs()
                    count += copyPresetsRecursive(doc, sub)
                }
                doc.name?.endsWith(".milk") == true || doc.name?.endsWith(".milk2") == true -> {
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        File(destDir, doc.name!!).outputStream().use { output -> input.copyTo(output) }
                    }
                    count++
                }
            }
        }
        return count
    }

    fun allPresets(): List<PresetEntry> {
        val favorites = loadStore().getJSONArray("favorites").toStringList().toSet()
        val files = mutableListOf<File>()
        bundledDir.walkTopDown().filter { it.isFile }.forEach { files.add(it) }
        importedDir.walkTopDown().filter { it.isFile }.forEach { files.add(it) }
        return files.map { f ->
            PresetEntry(path = f.absolutePath, name = f.nameWithoutExtension, favorite = f.absolutePath in favorites)
        }.sortedBy { it.name.lowercase() }
    }

    fun favoritePresets(): List<PresetEntry> = allPresets().filter { it.favorite }

    fun setFavorite(path: String, favorite: Boolean) {
        val store = loadStore()
        val favs = store.getJSONArray("favorites").toStringList().toMutableSet()
        if (favorite) favs.add(path) else favs.remove(path)
        store.put("favorites", JSONArray(favs.toList()))
        saveStore(store)
    }

    fun playlistNames(): List<String> {
        val store = loadStore()
        return store.getJSONObject("playlists").keys().asSequence().toList().sorted()
    }

    fun playlist(name: String): List<String> {
        val store = loadStore()
        val playlists = store.getJSONObject("playlists")
        if (!playlists.has(name)) return emptyList()
        return playlists.getJSONArray(name).toStringList()
    }

    fun savePlaylist(name: String, paths: List<String>) {
        val store = loadStore()
        store.getJSONObject("playlists").put(name, JSONArray(paths))
        saveStore(store)
    }

    fun deletePlaylist(name: String) {
        val store = loadStore()
        store.getJSONObject("playlists").remove(name)
        saveStore(store)
    }

    private fun loadStore(): JSONObject {
        if (!storeFile.exists()) {
            return JSONObject().apply {
                put("favorites", JSONArray())
                put("playlists", JSONObject())
            }
        }
        return JSONObject(storeFile.readText()).apply {
            if (!has("favorites")) put("favorites", JSONArray())
            if (!has("playlists")) put("playlists", JSONObject())
        }
    }

    private fun saveStore(obj: JSONObject) {
        storeFile.writeText(obj.toString())
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
