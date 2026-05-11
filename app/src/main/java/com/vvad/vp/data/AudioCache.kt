package com.vvad.vp.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import java.io.File

@UnstableApi
object AudioCache {
    private const val CACHE_SIZE_BYTES = 512L * 1024L * 1024L

    @Volatile
    private var sharedCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return sharedCache ?: synchronized(this) {
            sharedCache ?: SimpleCache(
                File(appContext.filesDir, "audio_cache"),
                LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
                StandaloneDatabaseProvider(appContext)
            ).also { sharedCache = it }
        }
    }

    fun buildUpstreamDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(NavidromeManager.CLIENT_NAME)
            .setAllowCrossProtocolRedirects(true)
    }

    fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache(context))
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
