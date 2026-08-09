package com.asfaltosonoro.projectmoverlay

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Prova a caricare libprojectmoverlay-jni.so PRIMA che qualcosa tocchi
 * ProjectMBridge (che altrimenti farebbe fallire System.loadLibrary in modo
 * non catturabile in modo pulito, e da quel momento la classe resterebbe
 * "avvelenata" per il resto della vita del processo).
 *
 * Se il caricamento fallisce, produce un report leggibile con le ABI del
 * telefono e il contenuto reale della cartella delle librerie native
 * dell'app installata, così capiamo se il file .so manca davvero dall'APK
 * o se il problema è un altro (es. dipendenza mancante).
 */
object NativeLibDiagnostics {

    fun tryLoadAndDiagnose(context: Context): String? {
        return try {
            System.loadLibrary("projectmoverlay-jni")
            null
        } catch (t: Throwable) {
            buildString {
                appendLine("Errore caricamento libreria nativa:")
                appendLine(t.toString())
                appendLine()
                appendLine("ABI supportate dal telefono: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Cartella libs nativa dell'app: ${context.applicationInfo.nativeLibraryDir}")
                val dir = File(context.applicationInfo.nativeLibraryDir)
                val contents = dir.list()
                appendLine("Contenuto cartella: ${contents?.joinToString() ?: "(vuota o non leggibile)"}")
            }
        }
    }
}
