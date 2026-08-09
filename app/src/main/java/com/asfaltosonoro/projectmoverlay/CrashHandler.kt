package com.asfaltosonoro.projectmoverlay

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Cattura i crash e salva lo stack trace su file, così è leggibile riaprendo
 * l'app anche senza PC/terminale (vedi MainActivity.showLastCrashIfAny()).
 */
class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            File(context.filesDir, "crash_log.txt").writeText(sw.toString())
        } catch (_: Exception) {
            // se anche il salvataggio fallisce non c'è molto da fare
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
