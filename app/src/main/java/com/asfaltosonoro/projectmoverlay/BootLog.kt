package com.asfaltosonoro.projectmoverlay

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Log "grezzo" scritto su disco passo-passo, con flush immediato ad ogni riga.
 * Serve a capire fino a dove arriva l'avvio dell'app anche quando il crash è
 * nativo (C++/OpenGL) e quindi non passa dal CrashHandler basato su eccezioni
 * Java: l'ultima riga scritta prima del silenzio è il punto in cui è morto.
 */
object BootLog {
    private const val FILE_NAME = "boot_log.txt"

    fun reset(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    fun log(context: Context, msg: String) {
        try {
            FileOutputStream(File(context.filesDir, FILE_NAME), true).use { fos ->
                fos.write("${System.currentTimeMillis()}: $msg\n".toByteArray())
                fos.flush()
            }
        } catch (_: Exception) {
            // se anche scrivere il log fallisce non c'è molto da fare
        }
    }

    fun readPreviousRunLog(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }
}
