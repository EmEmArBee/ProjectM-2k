cmake_minimum_required(VERSION 3.22.1)
project(projectmoverlay)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# libprojectM viene incluso come git submodule in app/src/main/cpp/projectm
# (vedi istruzioni: git submodule add https://github.com/projectM-visualizer/projectm.git
#  app/src/main/cpp/projectm)
# Le opzioni ENABLE_GLES / BUILD_SHARED_LIBS ecc. arrivano da build.gradle.kts
# tramite externalNativeBuild -> arguments.
# ---------------------------------------------------------------------------
add_subdirectory(projectm)

add_library(projectmoverlay-jni SHARED
    native-lib.cpp
)

# projectM (incluso sopra) imposta CMAKE_DEBUG_POSTFIX a "d" per le build
# Debug: senza questa riga il nostro .so verrebbe chiamato
# "libprojectmoverlay-jnid.so" invece di "libprojectmoverlay-jni.so", e
# System.loadLibrary("projectmoverlay-jni") in Kotlin non lo troverebbe più.
set_target_properties(projectmoverlay-jni PROPERTIES DEBUG_POSTFIX "")

find_library(log-lib log)
find_library(gles3-lib GLESv3)
find_library(egl-lib EGL)

target_link_libraries(projectmoverlay-jni
    libprojectM::projectM
    ${log-lib}
    ${gles3-lib}
    ${egl-lib}
)
