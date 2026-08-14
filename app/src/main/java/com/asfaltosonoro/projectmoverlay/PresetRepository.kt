package com.asfaltosonoro.projectmoverlay

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PresetEntry(val path: String, val name: String, val favorite: Boolean) {
    /** Molti preset MilkDrop seguono la convenzione "Autore - Titolo.milk". */
    val author: String? = name.substringBefore(" - ", missingDelimiterValue = "").ifEmpty { null }
    val title: String = if (author != null) name.substringAfter(" - ") else name
}

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
            val abs = f.absolutePath
            PresetEntry(path = abs, name = f.nameWithoutExtension, favorite = toRelative(abs) in favorites)
        }.sortedBy { it.name.lowercase() }
    }

    fun favoritePresets(): List<PresetEntry> = allPresets().filter { it.favorite }

    fun isFavorite(path: String): Boolean =
        loadStore().getJSONArray("favorites").toStringList().contains(toRelative(path))

    fun setFavorite(path: String, favorite: Boolean) {
        val store = loadStore()
        val favs = store.getJSONArray("favorites").toStringList().toMutableSet()
        val rel = toRelative(path)
        if (favorite) favs.add(rel) else favs.remove(rel)
        store.put("favorites", JSONArray(favs.toList()))
        saveStore(store)
    }

    fun playlistNames(): List<String> {
        val store = loadStore()
        return store.getJSONObject("playlists").keys().asSequence().toList().sorted()
    }

    /** Preset di una playlist, come percorsi assoluti pronti da caricare. */
    fun playlist(name: String): List<String> = playlistRaw(name).map { toAbsolute(it) }

    private fun playlistRaw(name: String): List<String> {
        val store = loadStore()
        val playlists = store.getJSONObject("playlists")
        if (!playlists.has(name)) return emptyList()
        return playlists.getJSONArray(name).toStringList()
    }

    fun savePlaylist(name: String, absolutePaths: List<String>) {
        savePlaylistRaw(name, absolutePaths.map { toRelative(it) })
    }

    private fun savePlaylistRaw(name: String, relativePaths: List<String>) {
        val store = loadStore()
        store.getJSONObject("playlists").put(name, JSONArray(relativePaths))
        saveStore(store)
    }

    /** Aggiunge un singolo preset a una playlist esistente (o appena creata). */
    fun addToPlaylist(playlistName: String, presetPath: String) {
        val current = playlistRaw(playlistName).toMutableList()
        val rel = toRelative(presetPath)
        if (rel !in current) current.add(rel)
        savePlaylistRaw(playlistName, current)
    }

    fun deletePlaylist(name: String) {
        val store = loadStore()
        store.getJSONObject("playlists").remove(name)
        saveStore(store)
    }

    /** Backup di preferiti e playlist in un unico JSON, esportabile su file. */
    fun exportBackupJson(): String = loadStore().toString(2)

    /** Ripristina preferiti e playlist da un JSON prodotto da exportBackupJson().
     * I percorsi sono relativi a bundled/imported: funziona anche dopo una
     * reinstallazione, purché i preset importati vengano re-importati dalla
     * stessa cartella (stessa struttura/nomi file). */
    fun importBackupJson(json: String) {
        val incoming = JSONObject(json)
        if (!incoming.has("favorites")) incoming.put("favorites", JSONArray())
        if (!incoming.has("playlists")) incoming.put("playlists", JSONObject())
        saveStore(incoming)
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

    /** bundled/imported → percorso relativo, stabile tra reinstallazioni (usato
     * per salvare preferiti/playlist in modo portabile per il backup). */
    private fun toRelative(absolutePath: String): String {
        val bundledPrefix = bundledDir.absolutePath + File.separator
        val importedPrefix = importedDir.absolutePath + File.separator
        return when {
            absolutePath.startsWith(bundledPrefix) -> "bundled/" + absolutePath.removePrefix(bundledPrefix)
            absolutePath.startsWith(importedPrefix) -> "imported/" + absolutePath.removePrefix(importedPrefix)
            else -> absolutePath // non dovrebbe capitare, ma non blocchiamo per questo
        }
    }

    private fun toAbsolute(stored: String): String = when {
        stored.startsWith("bundled/") -> File(bundledDir, stored.removePrefix("bundled/")).absolutePath
        stored.startsWith("imported/") -> File(importedDir, stored.removePrefix("imported/")).absolutePath
        else -> stored // retrocompatibilità con eventuali percorsi assoluti già salvati
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
