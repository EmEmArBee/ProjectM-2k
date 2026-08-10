package com.asfaltosonoro.projectmoverlay

import android.content.Context
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ProjectMRenderer(
    private val context: Context,
    // richiamato subito dopo nativeInit(), sempre sul thread GL: serve a
    // ripristinare l'ultimo preset scelto quando il contesto OpenGL viene
    // ricreato (es. dopo essere tornati dalle Impostazioni), altrimenti
    // projectM riparte sempre dal suo preset predefinito.
    private val onSurfaceReady: () -> Unit = {}
) : GLSurfaceView.Renderer {

    private var firstFrameLogged = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        BootLog.log(context, "renderer.onSurfaceCreated: chiamo nativeInit()")
        ProjectMBridge.nativeInit(1, 1)
        BootLog.log(context, "renderer.onSurfaceCreated: nativeInit() tornato OK")
        onSurfaceReady()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        BootLog.log(context, "renderer.onSurfaceChanged ${width}x${height}: chiamo nativeResize()")
        ProjectMBridge.nativeResize(width, height)
        BootLog.log(context, "renderer.onSurfaceChanged: nativeResize() tornato OK")
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!firstFrameLogged) {
            BootLog.log(context, "renderer.onDrawFrame: primo frame, chiamo nativeRenderFrame()")
        }
        ProjectMBridge.nativeRenderFrame()
        if (!firstFrameLogged) {
            BootLog.log(context, "renderer.onDrawFrame: primo frame disegnato OK")
            firstFrameLogged = true
        }
    }
}
