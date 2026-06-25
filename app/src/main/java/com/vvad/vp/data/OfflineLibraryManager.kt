package com.vvad.vp.data

import android.content.Context
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

class OfflineLibraryManager(context: Context, private val credentialsManager: CredentialsManager) {
    private val appContext = context.applicationContext
    private val offlineDir = File(appContext.filesDir, "offline")
    private val metadataDir = File(offlineDir, "metadata").apply { mkdirs() }
    private val tracksDir = File(offlineDir, "tracks").apply { mkdirs() }
    private val coversDir = File(offlineDir, "covers").apply { mkdirs() }

    init {
        // Clean up old SimpleCache-based storage
        File(appContext.filesDir, "offline_audio_cache").deleteRecursively()
        File(appContext.filesDir, "offline_library").deleteRecursively()
    }

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
        metadataDir.listFiles()
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
        val details = readAlbumRecord(albumId)?.details
        if (details != null) {
            clearAlbumAudioCache(details)
        }
        albumFile(albumId).delete()
        File(coversDir, "$albumId.jpg").delete()
    }

    suspend fun clearAlbumAudioCache(details: AlbumDetails) = withContext(Dispatchers.IO) {
        details.tracks.forEach { track ->
            deleteTrackFile(track.id)
        }
    }

    suspend fun clearAlbumAudioCache(albumId: String) = withContext(Dispatchers.IO) {
        val details = readAlbumRecord(albumId)?.details ?: return@withContext
        clearAlbumAudioCache(details)
    }

    suspend fun getCachedTrackIds(albumId: String): Set<String> = withContext(Dispatchers.IO) {
        val cachedIds = allCachedTrackIds()
        readAlbumRecord(albumId)?.details
            ?.tracks
            ?.filter { it.id in cachedIds }
            ?.mapTo(linkedSetOf()) { it.id }
            ?: emptySet()
    }

    fun getLocalTrackFile(trackId: String): File? {
        return tracksDir.listFiles()
            ?.firstOrNull { it.name.startsWith("$trackId.") && it.isFile }
    }

    suspend fun downloadAlbumForOffline(
        details: AlbumDetails,
        navidromeManager: NavidromeManager,
        onProgress: (currentTrack: Int, totalTracks: Int, trackName: String) -> Unit = { _, _, _ -> }
    ): OfflineDownloadResult = withContext(Dispatchers.IO) {
        val cachedAlbum = cacheAlbum(details)
        val format = navidromeManager.credentialsManager.getPreferredFormat()
        var downloadedTracks = 0

        cachedAlbum.tracks.forEachIndexed { index, track ->
            onProgress(index + 1, cachedAlbum.tracks.size, track.title)

            try {
                val streamUrl = navidromeManager.getStreamUrl(track.id)
                val trackFormat = downloadTrack(streamUrl, track.id, format)
                if (trackFormat != null) {
                    downloadedTracks++
                }
            } catch (e: Exception) {
                // Continue with next track on failure
            }
        }

        writeAlbumRecord(cachedAlbum, downloadedTracks == cachedAlbum.tracks.size)
        OfflineDownloadResult(downloadedTracks, cachedAlbum.tracks.size, cachedAlbum)
    }

    private fun downloadTrack(streamUrl: String, trackId: String, preferredFormat: String): String? {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000

        val actualFormat = try {
            if (preferredFormat == "raw") {
                detectFormat(connection.contentType)
            } else {
                preferredFormat
            }
        } catch (_: Exception) {
            preferredFormat
        }

        val tmpFile = File(tracksDir, "$trackId.tmp")
        val finalFile = File(tracksDir, "$trackId.$actualFormat")

        try {
            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            if (tmpFile.length() > 0) {
                tmpFile.renameTo(finalFile)
                return actualFormat
            }
            tmpFile.delete()
            return null
        } catch (e: Exception) {
            tmpFile.delete()
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun detectFormat(contentType: String?): String {
        if (contentType == null) return "mp3"
        return when {
            contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
            contentType.contains("ogg") -> "ogg"
            contentType.contains("flac") -> "flac"
            contentType.contains("aac") || contentType.contains("m4a") -> "aac"
            contentType.contains("wav") || contentType.contains("wave") -> "wav"
            else -> "mp3"
        }
    }

    private fun deleteTrackFile(trackId: String) {
        tracksDir.listFiles()
            ?.filter { it.name.startsWith("$trackId.") && it.isFile }
            ?.forEach { it.delete() }
    }

    private fun allCachedTrackIds(): Set<String> {
        return tracksDir.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name
                val dotIndex = name.lastIndexOf('.')
                if (dotIndex > 0) name.substring(0, dotIndex) else null
            }
            .toSet()
    }

    private fun getCachedTrackCount(details: AlbumDetails): Int {
        val cachedIds = allCachedTrackIds()
        return details.tracks.count { it.id in cachedIds }
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
                tracks = tracks.toMutableList()
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

    private fun albumFile(albumId: String): File = File(metadataDir, "$albumId.json")

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
