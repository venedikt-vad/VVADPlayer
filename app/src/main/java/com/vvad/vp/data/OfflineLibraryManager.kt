package com.vvad.vp.data

import android.content.Context
import androidx.media3.common.C
import com.vvad.vp.ui.models.Album
import com.vvad.vp.ui.models.AlbumDetails
import com.vvad.vp.ui.models.Track
import com.vvad.vp.ui.models.TrackArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.CacheWriter

data class OfflineDownloadResult(
    val downloadedTracks: Int,
    val totalTracks: Int,
    val album: AlbumDetails
)

data class AlbumOfflineAvailability(
    val downloadedForOffline: Boolean,
    val cachedTrackCount: Int,
    val totalTrackCount: Int
)

@UnstableApi
class OfflineLibraryManager(context: Context, private val credentialsManager: CredentialsManager) {
    private val appContext = context.applicationContext
    private val albumsDir = File(appContext.filesDir, "offline_library/albums").apply { mkdirs() }
    private val coversDir = File(appContext.filesDir, "offline_library/covers").apply { mkdirs() }

    suspend fun cacheAlbum(details: AlbumDetails): AlbumDetails = withContext(Dispatchers.IO) {
        val existingDownloaded = readAlbumRecord(details.id)?.downloadedForOffline ?: false
        val localCover = cacheCover(details.id, details.coverArtUrl) ?: details.coverArtUrl
        val cachedAlbum = details.copy(coverArtUrl = localCover)
        writeAlbumRecord(cachedAlbum, existingDownloaded)
        cachedAlbum
    }

    suspend fun getCachedAlbum(albumId: String): AlbumDetails? = withContext(Dispatchers.IO) {
        readAlbumRecord(albumId)?.details
    }

    suspend fun getOfflineAlbums(): List<Album> = withContext(Dispatchers.IO) {
        albumsDir.listFiles()
            .orEmpty()
            .mapNotNull { file -> readAlbumRecord(file.nameWithoutExtension) }
            .filter { record ->
                record.downloadedForOffline || getCachedTrackCount(record.details) > 0
            }
            .map { record ->
                Album(
                    id = record.details.id,
                    name = record.details.name,
                    artist = record.details.artist,
                    artistId = record.details.artistId,
                    coverArtUrl = record.details.coverArtUrl
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun isAlbumDownloaded(albumId: String): Boolean = withContext(Dispatchers.IO) {
        readAlbumRecord(albumId)?.downloadedForOffline == true
    }

    suspend fun getAlbumAvailability(albumId: String): AlbumOfflineAvailability? =
        withContext(Dispatchers.IO) {
            val record = readAlbumRecord(albumId)
            record?.details?.let { details ->
                AlbumOfflineAvailability(
                    downloadedForOffline = record.downloadedForOffline,
                    cachedTrackCount = getCachedTrackCount(details),
                    totalTrackCount = details.tracks.size
                )
            }
        }

    suspend fun removeFromCache(albumId: String) = withContext(Dispatchers.IO) {
        albumFile(albumId).delete()
        File(coversDir, "$albumId.jpg").delete()
    }

    suspend fun clearAlbumAudioCache(details: AlbumDetails) = withContext(Dispatchers.IO) {
        val cache = AudioCache.getCache(appContext, credentialsManager)
        details.tracks.forEach { track ->
            val keysToRemove = cache.getKeys().filter { it.startsWith("track:${track.id}:") }
            keysToRemove.forEach { key -> cache.removeResource(key) }
        }
    }

    suspend fun clearAlbumAudioCache(albumId: String) = withContext(Dispatchers.IO) {
        val details = readAlbumRecord(albumId)?.details ?: return@withContext
        clearAlbumAudioCache(details)
    }

    suspend fun getCachedTrackIds(albumId: String): Set<String> = withContext(Dispatchers.IO) {
        readAlbumRecord(albumId)?.details
            ?.tracks
            ?.filter { track -> isTrackCachedOffline(track.id) }
            ?.mapTo(linkedSetOf()) { track -> track.id }
            ?: emptySet()
    }

    suspend fun downloadAlbumForOffline(
        details: AlbumDetails,
        navidromeManager: NavidromeManager,
        onProgress: (currentTrack: Int, totalTracks: Int, trackName: String) -> Unit = { _, _, _ -> }
    ): OfflineDownloadResult = withContext(Dispatchers.IO) {
        val cachedAlbum = cacheAlbum(details)
        val format = navidromeManager.credentialsManager.getPreferredFormat()
        val bitrate = navidromeManager.credentialsManager.getMaxBitrate()
        val cacheDataSourceFactory = AudioCache.buildCacheDataSourceFactory(appContext, navidromeManager.credentialsManager)
        var downloadedTracks = 0

        cachedAlbum.tracks.forEachIndexed { index, track ->
            onProgress(index + 1, cachedAlbum.tracks.size, track.title)
            val streamUrl = navidromeManager.getStreamUrl(track.id)
            val cacheKey = AudioCache.buildTrackCacheKey(track.id, format, bitrate)
            val dataSpec = DataSpec.Builder()
                .setUri(streamUrl)
                .setKey(cacheKey)
                .build()

            val writer = CacheWriter(
                cacheDataSourceFactory.createDataSourceForDownloading(),
                dataSpec,
                null,
                null
            )
            writer.cache()
            downloadedTracks++
        }

        writeAlbumRecord(cachedAlbum, downloadedTracks == cachedAlbum.tracks.size)
        OfflineDownloadResult(downloadedTracks, cachedAlbum.tracks.size, cachedAlbum)
    }

    private fun readAlbumRecord(albumId: String): CachedAlbumRecord? {
        val file = albumFile(albumId)
        if (!file.exists()) return null

        return runCatching {
            val json = JSONObject(file.readText())
            val tracks = json.optJSONArray("tracks")?.toTracks().orEmpty()

            val details = AlbumDetails(
                id = json.getString("id"),
                name = json.getString("name"),
                artist = json.optString("artist", "Unknown"),
                artistId = json.optString("artistId", ""),
                year = if (json.has("year") && !json.isNull("year")) json.optInt("year") else null,
                coverArtUrl = json.optString("coverArtUrl", ""),
                tracks = tracks.toMutableList()   // ← FIXED: Convert to MutableList
            )

            CachedAlbumRecord(
                details = details,
                downloadedForOffline = json.optBoolean("downloadedForOffline", false)
            )
        }.getOrNull()
    }

    private fun writeAlbumRecord(details: AlbumDetails, downloadedForOffline: Boolean) {
        val json = JSONObject().apply {
            put("id", details.id)
            put("name", details.name)
            put("artist", details.artist)
            put("artistId", details.artistId)
            if (details.year != null) {
                put("year", details.year)
            } else {
                put("year", JSONObject.NULL)
            }
            put("coverArtUrl", details.coverArtUrl)
            put("downloadedForOffline", downloadedForOffline)
            put("tracks", details.tracks.toJson())
        }

        albumFile(details.id).writeText(json.toString())
    }

    private fun cacheCover(albumId: String, sourceUrl: String): String? {
        if (sourceUrl.isBlank()) return null
        if (!sourceUrl.startsWith("http", ignoreCase = true)) return sourceUrl

        val destination = File(coversDir, "$albumId.jpg")
        if (destination.exists() && destination.length() > 0L) {
            return destination.absolutePath
        }

        return runCatching {
            val connection = URL(sourceUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destination.absolutePath
        }.getOrNull()
    }

    private fun albumFile(albumId: String): File = File(albumsDir, "$albumId.json")

    private fun getCachedTrackCount(details: AlbumDetails): Int {
        return details.tracks.count { track -> isTrackCachedOffline(track.id) }
    }

    private fun isTrackCachedOffline(trackId: String): Boolean {
        val cache = AudioCache.getCache(appContext)
        val matchingKeys = cache.getKeys().filter { it.startsWith("track:$trackId:") }
        return matchingKeys.any { key ->
            val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            contentLength != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0L, contentLength)
        }
    }

    private fun JSONArray.toTracks(): List<Track> {
        return List(length()) { index ->
            val trackJson = getJSONObject(index)
            Track(
                id = trackJson.getString("id"),
                title = trackJson.getString("title"),
                artists = trackJson.optJSONArray("artists")?.toArtists().orEmpty(),
                number = trackJson.optInt("number", 0),
                discNumber = trackJson.optInt("discNumber", 1),
                duration = trackJson.optInt("duration", 0)
            )
        }
    }

    private fun JSONArray.toArtists(): List<TrackArtist> {
        return List(length()) { index ->
            val artistJson = getJSONObject(index)
            TrackArtist(
                id = artistJson.optString("id", ""),
                name = artistJson.optString("name", "Unknown")
            )
        }
    }

    private fun List<Track>.toJson(): JSONArray {
        return JSONArray().apply {
            forEach { track ->
                put(
                    JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("number", track.number)
                        put("discNumber", track.discNumber)
                        put("duration", track.duration)
                        put(
                            "artists",
                            JSONArray().apply {
                                track.artists.forEach { artist ->
                                    put(
                                        JSONObject().apply {
                                            put("id", artist.id)
                                            put("name", artist.name)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }
    }

    private data class CachedAlbumRecord(
        val details: AlbumDetails,
        val downloadedForOffline: Boolean
    )
}
