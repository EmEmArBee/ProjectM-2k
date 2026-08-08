package com.asfaltosonoro.projectmoverlay

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ProjectMRenderer : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ProjectMBridge.nativeInit(1, 1)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        ProjectMBridge.nativeResize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        ProjectMBridge.nativeRenderFrame()
    }
}
