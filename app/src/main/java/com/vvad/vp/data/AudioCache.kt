package com.vvad.vp.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import java.io.File

@UnstableApi
object AudioCache {

    private class NoopCacheEvictor : CacheEvictor {
        override fun requiresCacheSpanTouches(): Boolean = false
        override fun onCacheInitialized() {}
        override fun onStartFile(cache: Cache, key: String, position: Long, maxLength: Long) {}
        override fun onSpanAdded(cache: Cache, span: CacheSpan) {}
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {}
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {}
    }
    @Volatile
    private var sharedCache: SimpleCache? = null
    @Volatile
    private var sharedOfflineCache: SimpleCache? = null

    fun getCache(context: Context, cacheSizeMb: Int = 512, unlimited: Boolean = false): SimpleCache {
        val appContext = context.applicationContext
        return sharedCache ?: synchronized(this) {
            sharedCache ?: SimpleCache(
                File(appContext.filesDir, "audio_cache"),
                if (unlimited) NoopCacheEvictor() else LeastRecentlyUsedCacheEvictor(cacheSizeMb.toLong() * 1024L * 1024L),
                StandaloneDatabaseProvider(appContext)
            ).also { sharedCache = it }
        }
    }

    fun getOfflineCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return sharedOfflineCache ?: synchronized(this) {
            sharedOfflineCache ?: SimpleCache(
                File(appContext.filesDir, "offline_audio_cache"),
                NoopCacheEvictor(),
                StandaloneDatabaseProvider(appContext)
            ).also { sharedOfflineCache = it }
        }
    }

    fun resetOfflineCache() {
        sharedOfflineCache = null
    }

    suspend fun getCache(context: Context, credentialsManager: CredentialsManager): SimpleCache {
        val unlimited = credentialsManager.isCacheUnlimited()
        val size = credentialsManager.getCacheSizeMb()
        return getCache(context, size, unlimited)
    }

    fun resetCache() {
        sharedCache = null
    }

    fun buildUpstreamDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(NavidromeManager.CLIENT_NAME)
            .setAllowCrossProtocolRedirects(true)
    }

    fun buildCacheDataSourceFactory(context: Context, cacheSizeMb: Int = 512, unlimited: Boolean = false): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache(context, cacheSizeMb, unlimited))
            .setUpstreamDataSourceFactory(buildUpstreamDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    suspend fun buildCacheDataSourceFactory(context: Context, credentialsManager: CredentialsManager): CacheDataSource.Factory {
        val unlimited = credentialsManager.isCacheUnlimited()
        val size = credentialsManager.getCacheSizeMb()
        return buildCacheDataSourceFactory(context, size, unlimited)
    }

    fun buildOfflineCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getOfflineCache(context))
            .setUpstreamDataSourceFactory(buildUpstreamDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun buildTrackCacheKey(trackId: String, format: String, bitrate: Int): String {
        val normalizedBitrate = if (format == "raw") 0 else bitrate
        return "track:$trackId:format=$format:bitrate=$normalizedBitrate"
    }

    fun buildExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING or
                    Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
            )
    }
}
