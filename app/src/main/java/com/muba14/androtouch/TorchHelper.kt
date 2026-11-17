package com.muba14.androtouch

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Feneri açıp kapatır. Fener için kamera izni gerekmez. */
class TorchHelper(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchOn = false

    private val cameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            val ch = cameraManager.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    // Fener başka bir yerden (hızlı ayarlar vb.) açılıp kapanırsa durumu takip et.
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) torchOn = enabled
        }
    }

    init {
        runCatching { cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    }

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return runCatching { cameraManager.setTorchMode(id, !torchOn) }.isSuccess
    }

    fun release() {
        runCatching { cameraManager.unregisterTorchCallback(callback) }
    }
}
