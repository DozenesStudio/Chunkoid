package com.dozenesstudio.chunkoid.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object BackgroundUtils {

    private const val BRIGHTNESS_MULTIPLIER = 0.65f

    fun applyDarkening(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val colorMatrix = ColorMatrix().apply {
            setScale(BRIGHTNESS_MULTIPLIER, BRIGHTNESS_MULTIPLIER, BRIGHTNESS_MULTIPLIER, 1.0f)
        }
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(mutableBitmap, 0f, 0f, paint)
        return mutableBitmap
    }
}
