package com.asfaltosonoro.projectmoverlay

object ProjectMBridge {
    init {
        System.loadLibrary("projectmoverlay-jni")
    }

    external fun nativeInit(width: Int, height: Int)
    external fun nativeResize(width: Int, height: Int)
    external fun nativeRenderFrame()
    external fun nativeLoadPresetFile(path: String, smoothTransition: Boolean)
    external fun nativePcmAdd(samples: ShortArray, channels: Int)
    external fun nativeDestroy()
}
