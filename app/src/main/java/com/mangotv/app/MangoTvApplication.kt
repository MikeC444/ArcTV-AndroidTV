package com.mangotv.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.mangotv.app.ui.home.heroImages
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.runBlocking

private const val HERO_WARM_IMAGES = 3

class MangoTvApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        warmUp()
    }

    // Work the first screen would otherwise do on the main thread (and the first frame would wait for): loading the
    // UI sounds, and reading Home's cached rows off disk. Both are lazy and thread-safe, so doing them here, off the
    // main thread, only means they are already finished by the time Home asks.
    private fun warmUp() {
        Thread {
            runCatching { container.uiSoundPlayer }
            // Temporary torrent data a killed run left behind; touches no torrent code unless there is something to delete.
            runCatching { container.torrentStreamManager.purgeStaleStorage() }
            // The cached hero's first pictures go into Coil's memory cache now, so the first slide is drawn from
            // memory the moment Home appears instead of being decoded from disk then.
            runCatching {
                runBlocking { container.homeCacheRepository.read() }?.first?.let { hero ->
                    heroImages(hero).take(HERO_WARM_IMAGES).forEach { image ->
                        imageLoader.enqueue(
                            ImageRequest.Builder(this).data(image.url).bitmapConfig(Bitmap.Config.RGB_565).build()
                        )
                    }
                }
            }
        }.apply {
            name = "mango-warmup"
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    // Home's poster grid got a lot denser recently -- smaller poster cards
    // mean more tiles visible per row, and catalog rows are now fanned out
    // by genre into many more rows, so the total set of distinct on-screen
    // images grew well past what Coil's default ~20%-of-heap memory cache
    // comfortably holds on Fire TV Stick hardware. Once that cache starts
    // thrashing, images that already loaded a moment ago get evicted and
    // re-decoded as the user keeps scrolling, which read as stutter. A
    // larger cache plus a short crossfade (so anything that does arrive a
    // beat late fades in instead of popping) both target that directly.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(150)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.35)
                .build()
        }
        // A poster that scrolled out of the memory cache -- or wasn't seen
        // in a previous session -- decodes from disk instead of a full
        // network re-fetch on revisit. Bounded rather than a bare
        // percentage of free space, since that can be tiny on an entry-
        // level Fire TV Stick's storage or needlessly large on a Cube.
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizePercent(0.03)
                .minimumMaxSizeBytes(50L * 1024 * 1024)
                .maximumMaxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .build()
}
