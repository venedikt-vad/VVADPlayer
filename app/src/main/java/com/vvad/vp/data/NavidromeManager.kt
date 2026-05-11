package com.vvad.vp.data

import com.vvad.vp.ui.models.Album
import com.vvad.vp.ui.models.AlbumDetails
import com.vvad.vp.ui.models.Track
import com.vvad.vp.ui.models.TrackArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NavidromeManager(
    val credentialsManager: CredentialsManager,
    private val offlineLibraryManager: OfflineLibraryManager? = null
) {
    data class AlbumFetchResult(
        val album: AlbumDetails?,
        val fromOfflineCache: Boolean
    )


    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "VVADPlayer"
        // Base parameters used in every request
        const val API_PARAMS = "v=$API_VERSION&c=$CLIENT_NAME"
    }

    suspend fun testConnection(server: String, user: String, pass: String, https: Boolean): Boolean = withContext(Dispatchers.IO) {
        val protocol = if (https) "https://" else "http://"
        val baseUrl = if (server.startsWith("http")) server else "$protocol$server"
        val salt = generateSalt()
        val token = md5(pass + salt)

        val pingUrl = "$baseUrl/rest/ping?u=$user&t=$token&s=$salt&$API_PARAMS&f=json"

        return@withContext try {
            val connection = URL(pingUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response).getJSONObject("subsonic-response")
                if (json.getString("status") == "ok") {
                    // Success! Save everything
                    credentialsManager.saveCredentials(server, user, pass, https)
                    credentialsManager.updateStatus("Connected")
                    true
                } else {
                    credentialsManager.updateStatus("Error: ${json.optJSONObject("error")?.optString("message") ?: "Unknown"}")
                    false
                }
            } else {
                credentialsManager.updateStatus("HTTP Error: $responseCode")
                false
            }
        } catch (e: Exception) {
            credentialsManager.updateStatus("Failed: ${e.localizedMessage}")
            false
        }
    }

    suspend fun fetchRandomAlbums(limit: Int = 10): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<Album>()
        val baseUrl = credentialsManager.getFullServerUrl()
        val user = credentialsManager.getUsername()
        val pass = credentialsManager.getPassword()

        if (baseUrl.isBlank() || user.isBlank()) return@withContext albums

        val salt = generateSalt()
        val token = md5(pass + salt)
        val auth = "u=$user&t=$token&s=$salt&$API_PARAMS"

        val apiUrl = "$baseUrl/rest/getAlbumList2?$auth&type=random&size=$limit&f=json"

        try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val subsonic = JSONObject(response).getJSONObject("subsonic-response")

                subsonic.optJSONObject("albumList2")?.optJSONArray("album")?.let { array ->
                    for (i in 0 until array.length()) {
                        val albumJson = array.getJSONObject(i)
                        val id = albumJson.getString("id")
                        val name = albumJson.getString("name")
                        val artist = albumJson.optString("artist", "Unknown Artist")
                        val artistId = albumJson.optString("artistId", "")

                        val coverArtUrl = "$baseUrl/rest/getCoverArt?u=$user&t=$token&s=$salt&$API_PARAMS&id=$id&size=250"

                        albums.add(Album(id, name, artist, artistId, coverArtUrl))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return@withContext albums
    }

    suspend fun fetchAlbum(albumId: String): AlbumDetails? = fetchAlbumWithSource(albumId).album

    suspend fun fetchAlbumWithSource(albumId: String): AlbumFetchResult = withContext(Dispatchers.IO) {
        val baseUrl = credentialsManager.getFullServerUrl()
        val user = credentialsManager.getUsername()
        val pass = credentialsManager.getPassword()
        val salt = generateSalt()
        val token = md5(pass + salt)
        val auth = "u=$user&t=$token&s=$salt&$API_PARAMS"

        val url = "$baseUrl/rest/getAlbum?$auth&id=$albumId&f=json"

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val albumJson = JSONObject(response).getJSONObject("subsonic-response").getJSONObject("album")

                val tracks = mutableListOf<Track>()
                val songArray = albumJson.optJSONArray("song")

                songArray?.let {
                    for (i in 0 until it.length()) {
                        val s = it.getJSONObject(i)

                        // Parse multiple artists
                        val artists = mutableListOf<TrackArtist>()
                        val artistArray = s.optJSONArray("artists")
                        if (artistArray != null) {
                            for (j in 0 until artistArray.length()) {
                                val artObj = artistArray.getJSONObject(j)
                                artists.add(TrackArtist(artObj.getString("id"), artObj.getString("name")))
                            }
                        } else {
                            // Fallback to single artist if array is missing
                            artists.add(TrackArtist(s.optString("artistId"), s.optString("artist")))
                        }

                        tracks.add(Track(
                            id = s.getString("id"),
                            title = s.getString("title"),
                            artists = artists,
                            number = s.optInt("track", 0),
                            discNumber = s.optInt("discNumber", 1),
                            duration = s.optInt("duration", 0)
                        ))
                    }
                }

                val fetchedAlbum = AlbumDetails(
                    id = albumJson.getString("id"),
                    name = albumJson.getString("name"),
                    artist = albumJson.optString("artist", "Unknown"),
                    artistId = albumJson.optString("artistId", ""),
                    year = albumJson.optInt("year"),
                    coverArtUrl = "$baseUrl/rest/getCoverArt?$auth&id=$albumId&size=600",
                    tracks = tracks.sortedWith(compareBy({ it.discNumber }, { it.number }))
                )
                return@withContext AlbumFetchResult(
                    album = offlineLibraryManager?.cacheAlbum(fetchedAlbum) ?: fetchedAlbum,
                    fromOfflineCache = false
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext AlbumFetchResult(
            album = offlineLibraryManager?.getCachedAlbum(albumId),
            fromOfflineCache = true
        )
    }

    suspend fun getStreamUrl(trackId: String): String {
        val baseUrl = credentialsManager.getFullServerUrl()
        val user = credentialsManager.getUsername()
        val pass = credentialsManager.getPassword()

        val format = credentialsManager.getPreferredFormat()
        val bitrate = credentialsManager.getMaxBitrate()
        val salt = generateSalt()
        val token = md5(pass + salt)
        val auth = "u=$user&t=$token&s=$salt&$API_PARAMS&id=$trackId"

        if (format == "raw") {
            // Use the original file endpoint directly so "RAW" never silently triggers transcoding.
            return "$baseUrl/rest/download?$auth"
        }

        var url = "$baseUrl/rest/stream?$auth&format=$format"
        if (bitrate > 0) {
            url += "&maxBitRate=$bitrate"
        }
        return url
    }

    private fun generateSalt(): String = (1..6).map {
        (('A'..'Z') + ('a'..'z') + ('0'..'9')).random()
    }.joinToString("")
}
