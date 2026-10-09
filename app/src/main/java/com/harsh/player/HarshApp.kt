package com.harsh.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.harsh.player.util.DeviceCapabilities

/**
 * HARSH Offline Video Player — Application entry point.
 *
 * Keeps global setup deliberately small. Coil is configured with tight memory
 * and disk caches so thumbnail loading never dominates the RAM budget on a
 * 1 GB Android TV.
 */
class HarshApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        DeviceCapabilities.init(this)
    }

    /**
     * Coil is used only for cached thumbnails. On low-RAM devices the memory
     * cache is kept to ~4 MB and the disk cache to ~24 MB, both of which are
     * far below Coil's defaults and safe on a 1 GB TV.
     */
    override fun newImageLoader(): ImageLoader {
        val lowRam = DeviceCapabilities.isLowRamDevice
        val memCacheMb = if (lowRam) 4 else 12
        val diskCacheMb = if (lowRam) 24 else 64
        return ImageLoader.Builder(this)
            .memoryCache(
                MemoryCache.Builder(this)
                    .maxSizeBytes(memCacheMb * 1024L * 1024L)
                    .build()
            )
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnails"))
                    .maxSizeBytes(diskCacheMb * 1024L * 1024L)
                    .build()
            )
            .crossfade(!lowRam)
            .build()
    }
}
