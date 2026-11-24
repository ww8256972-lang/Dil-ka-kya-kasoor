package com.waqar.institute.di

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * PerformanceModule: High-Performance Networking & Coil Image Loader Configuration.
 *
 * Provides:
 * 1. Singleton [OkHttpClient] with 50MB disk cache and intelligent offline caching.
 * 2. Custom [ImageLoader] for Coil with 15% RAM memory caching, 50MB disk caching,
 *    and crossfade animations for smooth 60/120fps scrolling.
 */
object PerformanceModule {

    @Volatile
    private var okHttpClientInstance: OkHttpClient? = null

    @Volatile
    private var imageLoaderInstance: ImageLoader? = null

    /**
     * Returns a production-tuned OkHttpClient with disk cache and connection reuse.
     */
    fun getOkHttpClient(context: Context): OkHttpClient {
        return okHttpClientInstance ?: synchronized(this) {
            okHttpClientInstance ?: buildOkHttpClient(context.applicationContext).also {
                okHttpClientInstance = it
            }
        }
    }

    /**
     * Builds and installs custom Coil ImageLoader as the application-wide default.
     */
    fun installCoilImageLoader(context: Context) {
        val loader = getImageLoader(context.applicationContext)
        Coil.setImageLoader(loader)
    }

    /**
     * Returns the singleton Coil ImageLoader with memory and disk cache constraints.
     */
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoaderInstance ?: synchronized(this) {
            imageLoaderInstance ?: buildImageLoader(context.applicationContext).also {
                imageLoaderInstance = it
            }
        }
    }

    private fun buildOkHttpClient(appContext: Context): OkHttpClient {
        val httpCacheDir = File(appContext.cacheDir, "http_cache").apply {
            if (!exists()) mkdirs()
        }
        val cacheSize = 50L * 1024L * 1024L // 50 MB disk cache

        return OkHttpClient.Builder()
            .cache(Cache(httpCacheDir, cacheSize))
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(OfflineCacheInterceptor(appContext))
            .build()
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        val imageCacheDir = File(appContext.cacheDir, "image_cache").apply {
            if (!exists()) mkdirs()
        }

        return ImageLoader.Builder(appContext)
            .okHttpClient { getOkHttpClient(appContext) }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.15) // Use up to 15% of device available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir)
                    .maxSizeBytes(50L * 1024L * 1024L) // 50 MB disk cache for images
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    /**
     * Interceptor providing seamless offline cache fallback for up to 7 days.
     */
    private class OfflineCacheInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            val isConnected = isNetworkAvailable(context)

            if (!isConnected) {
                // If offline, serve from cache with up to 7 days staleness
                request = request.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=" + (7 * 24 * 60 * 60))
                    .build()
            }

            return chain.proceed(request)
        }

        private fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
