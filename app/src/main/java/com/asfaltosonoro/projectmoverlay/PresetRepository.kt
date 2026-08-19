package com.asfaltosonoro.projectmoverlay

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

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

    /** Copia ricorsivamente tutti i .milk/.milk2 trovati sotto la cartella scelta
     * con SAF. Non fa mai crashare l'app: qualunque errore (spazio esaurito,
     * file corrotti, permessi persi) viene riportato via onFinished invece di
     * propagarsi. */
    fun importFolder(treeUri: Uri, onFinished: (importedCount: Int, errorMessage: String?) -> Unit) {
        Thread {
            try {
                if (!hasEnoughFreeSpace(MIN_FREE_SPACE_MB)) {
                    onFinished(0, "Spazio di archiviazione insufficiente sul dispositivo (serve almeno $MIN_FREE_SPACE_MB MB liberi)")
                    return@Thread
                }
                val root = DocumentFile.fromTreeUri(context, treeUri)
                if (root == null) {
                    onFinished(0, "Impossibile aprire la cartella scelta")
                    return@Thread
                }
                importedDir.mkdirs()
                val count = copyPresetsRecursive(root, importedDir)
                onFinished(count, null)
            } catch (e: OutOfMemoryError) {
                onFinished(0, "Memoria insufficiente per completare l'importazione (prova con una cartella più piccola)")
            } catch (e: Exception) {
                onFinished(0, "Errore durante l'importazione: ${e.message}")
            }
        }.start()
    }

    private fun copyPresetsRecursive(dir: DocumentFile, destDir: File): Int {
        var count = 0
        dir.listFiles().forEach { doc ->
            try {
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
            } catch (_: Exception) {
                // un singolo file rotto/illeggibile non deve far fallire tutto il resto
            }
        }
        return count
    }

    /** Scarica uno zip di preset da un URL (es. i pacchetti GitHub ufficiali di
     * projectM) e lo scompatta direttamente nella cartella preset importati.
     * Pensato per chi non vuole scaricare/scompattare manualmente sul telefono. */
    fun downloadAndExtractPresetPack(
        urlString: String,
        onProgress: (percent: Int) -> Unit,
        onFinished: (importedCount: Int, errorMessage: String?) -> Unit
    ) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                if (!hasEnoughFreeSpace(MIN_FREE_SPACE_MB)) {
                    onFinished(0, "Spazio di archiviazione insufficiente sul dispositivo (serve almeno $MIN_FREE_SPACE_MB MB liberi)")
                    return@Thread
                }
                importedDir.mkdirs()
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    onFinished(0, "Il server ha risposto con errore ${connection.responseCode}")
                    return@Thread
                }
                val totalBytes = connection.contentLength.toLong()
                if (totalBytes > 0) {
                    // spazio per lo zip + una stima dell'estrazione + margine
                    val requiredMb = (totalBytes / (1024 * 1024)) * 2 + 100
                    if (!hasEnoughFreeSpace(requiredMb)) {
                        onFinished(0, "Spazio insufficiente per questo pacchetto: servono circa $requiredMb MB liberi")
                        return@Thread
                    }
                }
                var readBytes = 0L
                var count = 0

                ZipInputStream(connection.inputStream.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        // gli zip con una cartella radice tipo "presets-nome-master/"
                        // (comune sia sui mirror GitHub sia in molti archivi già
                        // ricompattati): la togliamo, teniamo solo il resto del percorso
                        val relativeName = name.substringAfter('/', name)
                        if (!entry.isDirectory && (name.endsWith(".milk") || name.endsWith(".milk2"))) {
                            val outFile = File(importedDir, relativeName)
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output -> zip.copyTo(output) }
                            count++
                        }
                        readBytes += entry.compressedSize.coerceAtLeast(0)
                        if (totalBytes > 0) onProgress(((readBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                onFinished(count, null)
            } catch (e: OutOfMemoryError) {
                onFinished(0, "Memoria insufficiente per completare il download")
            } catch (e: Exception) {
                onFinished(0, "Errore durante il download: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun hasEnoughFreeSpace(requiredMb: Long): Boolean {
        return try {
            val stat = StatFs(context.filesDir.path)
            val freeMb = stat.availableBytes / (1024 * 1024)
            freeMb >= requiredMb
        } catch (_: Exception) {
            true // se non riusciamo a controllare, non blocchiamo l'utente per questo
        }
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

    companion object {
        private const val MIN_FREE_SPACE_MB = 100L

        /** Pacchetti preset scaricabili direttamente dall'app. Link primario:
         * hosting personale (zip diretti, niente Google Drive da gestire).
         * mirrorInfoUrl: repository/sorgente originale, tenuto come backup e
         * come riferimento per la descrizione — non scaricato automaticamente
         * dall'app (nel caso del MegaPack è su Google Drive, non scaricabile
         * in modo affidabile senza intervento manuale). */
        val AVAILABLE_PACKS = listOf(
            PresetPack(
                displayName = "MilkDrop 135k+ Presets MegaPack",
                sizeLabel = "4,49 GB",
                description = "La collezione più grande in assoluto: oltre 130.000 preset, texture incluse. Scaricalo solo con tempo, connessione e spazio liberi in abbondanza.",
                downloadUrl = "https://www.marcobottecchia.it/ProjectM/MilkDrop%20135k%2B%20Presets%20MegaPack%202026.zip",
                mirrorInfoUrl = "https://drive.google.com/file/d/1DlszoqMG-pc5v1Bo9x4NhemGPiwT-0pv/view"
            ),
            PresetPack(
                displayName = "Cream of the Crop Pack",
                sizeLabel = "32,10 MB",
                description = "Circa 10.000 preset selezionati da Jason Fletcher — il pacchetto predefinito di projectM.",
                downloadUrl = "https://www.marcobottecchia.it/ProjectM/presets-cream-of-the-crop-master.zip",
                mirrorInfoUrl = "https://github.com/projectM-visualizer/presets-cream-of-the-crop"
            ),
            PresetPack(
                displayName = "Base Milkdrop Texture Pack",
                sizeLabel = "3,37 MB",
                description = "Texture di base, consigliato in aggiunta a qualsiasi altro pacchetto di preset.",
                downloadUrl = "https://www.marcobottecchia.it/ProjectM/presets-milkdrop-texture-pack-master.zip",
                mirrorInfoUrl = "https://github.com/projectM-visualizer/presets-milkdrop-texture-pack"
            ),
            PresetPack(
                displayName = "Classic projectM Presets",
                sizeLabel = "8,54 MB",
                description = "Poco più di 4.000 preset dalle versioni precedenti di projectM.",
                downloadUrl = "https://www.marcobottecchia.it/ProjectM/presets-projectm-classic-master.zip",
                mirrorInfoUrl = "https://github.com/projectM-visualizer/presets-projectm-classic"
            ),
            PresetPack(
                displayName = "Milkdrop 2 Presets (originali)",
                sizeLabel = "1,38 MB",
                description = "La collezione originale distribuita con Milkdrop e Winamp.",
                downloadUrl = "https://www.marcobottecchia.it/ProjectM/presets-milkdrop-original-master.zip",
                mirrorInfoUrl = "https://github.com/projectM-visualizer/presets-milkdrop-original"
            ),
            PresetPack(
                displayName = "Collezioni storiche projectM",
                sizeLabel = "8,39 MB",
                description = "bltc201, Milkdrop 1 e 2, projectM, tryptonaut, yin — le collezioni storicamente incluse con projectM.",
                downloadUrl = "https://www.marcobottecchia.it/ProjectM/projectm_presets.zip",
                mirrorInfoUrl = "http://spiegelmc.com/pub/projectm_presets.zip"
            )
        )
    }
}

data class PresetPack(
    val displayName: String,
    val sizeLabel: String,
    val description: String,
    val downloadUrl: String,
    val mirrorInfoUrl: String
)
