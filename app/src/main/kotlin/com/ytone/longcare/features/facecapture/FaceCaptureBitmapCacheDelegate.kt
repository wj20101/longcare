package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import java.lang.ref.WeakReference

internal object FaceCaptureBitmapCacheDelegate {

    fun cleanup(bitmapCache: MutableList<WeakReference<Bitmap>>) {
        bitmapCache.removeAll { it.get() == null }
    }

    fun add(bitmapCache: MutableList<WeakReference<Bitmap>>, bitmap: Bitmap) {
        bitmapCache.add(WeakReference(bitmap))
    }

    fun recycleAndRemoveAt(bitmapCache: MutableList<WeakReference<Bitmap>>, index: Int) {
        if (index in bitmapCache.indices) {
            bitmapCache[index].get()?.recycle()
            bitmapCache.removeAt(index)
        }
    }

    fun recycleAndClear(bitmapCache: MutableList<WeakReference<Bitmap>>) {
        bitmapCache.forEach { it.get()?.recycle() }
        bitmapCache.clear()
    }
}
