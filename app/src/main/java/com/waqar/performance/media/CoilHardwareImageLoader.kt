package com.waqar.performance.media

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.io.File

/**
 * CoilHardwareImageLoader: Hardware-Accelerated Coil Engine with Strict LRU Memory Caching.
 *
 * Configures hardware bitmap pipelines for zero frame drops:
 * - Allocates up to 20% of available device RAM for image caching.
 * - 50MB disk cache for offline student and faculty portraits.
 * - Hardware bitmap allocation offloads textures directly to GPU memory.
 */
object CoilHardwareImageLoader {

    @Volatile
    private var customLoader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return customLoader ?: synchronized(this) {
            customLoader ?: buildImageLoader(context.applicationContext).also {
                customLoader = it
            }
        }
    }

    fun install(context: Context) {
        val loader = get(context)
        Coil.setImageLoader(loader)
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        val cacheDir = File(context.cacheDir, "coil_hardware_cache").apply {
            if (!exists()) mkdirs()
        }

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // Up to 20% of RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(50L * 1024L * 1024L) // 50MB disk
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .allowHardware(true)
            .build()
    }
}
