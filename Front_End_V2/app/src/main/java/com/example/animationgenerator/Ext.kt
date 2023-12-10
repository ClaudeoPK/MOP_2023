package com.example.animationgenerator

import android.Manifest
import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics

object Ext {
    fun Int.checkIsUpperSdkVersion(): Boolean {
        return Build.VERSION.SDK_INT >= this
    }

    fun Activity.getDeviceSize(): List<Int> {
        var deviceWidth = 0
        var deviceHeight = 0
        val outMetrics = DisplayMetrics()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val display = this.display
            display?.getRealMetrics(outMetrics)
            deviceHeight = outMetrics.heightPixels
            deviceWidth = outMetrics.widthPixels
        } else {
            @Suppress("DEPRECATION")
            val display = this.windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(outMetrics)
            deviceHeight = outMetrics.heightPixels
            deviceWidth = outMetrics.widthPixels
        }
        return listOf(deviceWidth, deviceHeight)
    }

    val UPPER_TIRAMISU_READ_EXTERNAL_STORAGE = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    val UNDER_TIRAMISU_READ_EXTERNAL_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA
    )

    const val SELECTED_IMAGE_URI = "SELECTED_IMAGE_URI"
}