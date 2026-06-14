package com.vvad.vp.data

import android.util.Log
import com.vvad.vp.ui.models.Album
import com.vvad.vp.ui.models.AlbumDetails
import com.vvad.vp.ui.models.ArtistDetails
import com.vvad.vp.ui.models.SongSearchResult
import com.vvad.vp.ui.models.Track
import com.vvad.vp.ui.models.TrackArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class NavidromeManager(
    val credentialsManager: CredentialsManager,
    private val offlineLibraryManager: OfflineLibraryManager? = null
) {
    data class AlbumFetchResult(
        val album: AlbumDetails?,
        val fromOfflineCache: Boolean
    )

    companion object {
        private const val TAG = "NavidromeManager"
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "VVADPlayer"
        // Base parameters used in Subsonic fallback requests (Streaming & Images)
        const val API_PARAMS = "v=$API_VERSION&c=$CLIENT_NAME"
    }

    // Native API auth tokens
    private var nativeToken: String? = null
    private var nativeClientId: String = UUID.randomUUID().toString()

    // =========================================================================
    // PUBLIC API ENDPOINTS
    // =========================================================================

    /**
     * Tests connection to Navidrome server with detailed logging.
     * Also updates the status in CredentialsManager.
     */
    suspend fun testConnection(
        server: String,
        user: String,
        pass: String,
        https: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val tag = "Navidrome_ConnectionTest"

        Log.i(tag, "=== STARTING CONNECTION TEST ===")
        Log.i(tag, "Server: $server | HTTPS: $https | User: $user")

        try {
            val protocol = if (https) "https://" else "http://"
            val baseUrl = if (server.startsWith("http")) server else "$protocol$server"
            Log.i(tag, "Base URL: $baseUrl")

            // Clean up base URL
            val cleanBaseUrl = baseUrl.trimEnd('/')

            val authParams = buildSubsonicAuthParams(user, pass)
            val testUrl = "$cleanBaseUrl/rest/ping?$authParams&f=json"

            Log.i(tag, "Testing endpoint: $testUrl")

            val response = executeRequest(fullUrl = testUrl, method = "GET")

            return@withContext if (response != null) {
                Log.i(tag, "✅ Connection SUCCESSFUL")
                credentialsManager.updateStatus("Connected")
                true
            } else {
                Log.e(tag, "❌ Connection FAILED - No response or error")
                credentialsManager.updateStatus("Connection Failed")
                false
            }

        } catch (e: Exception) {
            Log.e(tag, "💥 Connection test crashed with exception", e)
            credentialsManager.updateStatus("Error: ${e::class.simpleName}")
            false
        }
    }

    /**
     * Resolves the proper streaming URL for a given track id.
     */
    suspend fun getStreamUrl(trackId: String): String {
        val baseUrl = credentialsManager.getFullServerUrl()
        val authParams = buildSubsonicAuthParams()
        val format = credentialsManager.getPreferredFormat()
        val bitrate = credentialsManager.getMaxBitrate()

        val fullAuth = "$authParams&id=$trackId"

        if (format == "raw") {
            // Use original file download endpoint directly to bypass transcoding engine entirely
            return "$baseUrl/rest/download?$fullAuth"
        }

        var url = "$baseUrl/rest/stream?$fullAuth&format=$format"
        if (bitrate > 0) {
            url += "&maxBitRate=$bitrate"
        }
        return url
    }

    /**
     * Returns cover art URL with optional size parameter.
     * @param coverArtId The ID of the cover
     * @param size Optional target size in pixels (Navidrome will scale to this)
     */
    suspend fun getCoverArtUrl(coverArtId: String, size: Int? = null): String {
        val baseUrl = credentialsManager.getFullServerUrl()
        if (baseUrl.isBlank() || coverArtId.isBlank()) return ""

        val authParams = buildSubsonicAuthParams()
        var url = "$baseUrl/rest/getCoverArt?$authParams&id=$coverArtId"

        size?.let {
            url += "&size=$it"
        }
        return url
    }

    /**
     * Fetches recently played albums using native API.
     */
    suspend fun getRecentlyPlayedAlbums(limit: Int = 10): List<Album> = withContext(Dispatchers.IO) {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while fetching recently played albums.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            // Use recently_played filter + sort by play_date
            val url = URL("$baseUrl/api/album?_sort=play_date&_order=DESC&_end=$limit&recently_played=true")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                debugJsonPayload("NavidromeManager_RecentlyPlayed", rawJson)

                val jsonArray = JSONArray(rawJson)
                return@withContext parseAlbumList(jsonArray)
            } else {
                Log.e(TAG, "Failed to fetch recently played albums. Response code: ${urlConnection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during recently played albums fetch", e)
        }
        return@withContext emptyList()
    }

    /**
     * Fetches most played albums (sorted by play count).
     */
    suspend fun getMostPlayedAlbums(limit: Int = 10): List<Album> = withContext(Dispatchers.IO) {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while fetching most played albums.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val url = URL("$baseUrl/api/album?_sort=play_count&_order=DESC&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                debugJsonPayload("NavidromeManager_MostPlayedAlbums", rawJson)

                val jsonArray = JSONArray(rawJson)
                return@withContext parseAlbumList(jsonArray)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during most played albums fetch", e)
        }
        return@withContext emptyList()
    }

    /**
     * Fetches most played artists.
     */
    suspend fun getMostPlayedArtists(limit: Int = 10): List<Album> = withContext(Dispatchers.IO) {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while fetching most played artists.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val url = URL("$baseUrl/api/artist?_sort=play_count&_order=DESC&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                debugJsonPayload("NavidromeManager_MostPlayedArtists", rawJson)

                val jsonArray = JSONArray(rawJson)
                val artistsList = mutableListOf<Album>()
                val smallSize = credentialsManager.getCoverSizeSmall()

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    artistsList.add(
                        Album(
                            id = json.optString("id"),
                            name = json.optString("name"),
                            artist = json.optString("name"),
                            artistId = json.optString("id"),
                            coverArtUrl = getCoverArtUrl(json.optString("id"), smallSize),
                            year = 0,
                            type = "artist"
                        )
                    )
                }
                return@withContext artistsList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during most played artists fetch", e)
        }
        return@withContext emptyList()
    }

    suspend fun getAlbums(limit: Int = 500, sort: String = "name", order: String = "ASC"): List<Album> = withContext(Dispatchers.IO) {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while fetching albums.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val encodedSort = URLEncoder.encode(sort, "UTF-8")
            val url = URL("$baseUrl/api/album?_sort=$encodedSort&_order=$order&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseAlbumList(JSONArray(rawJson))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during albums fetch", e)
        }
        return@withContext emptyList()
    }

    suspend fun getArtists(limit: Int = 500, sort: String = "name", order: String = "ASC"): List<Album> = withContext(Dispatchers.IO) {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while fetching artists.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val encodedSort = URLEncoder.encode(sort, "UTF-8")
            val url = URL("$baseUrl/api/artist?_sort=$encodedSort&_order=$order&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseArtistList(JSONArray(rawJson))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during artists fetch", e)
        }
        return@withContext emptyList()
    }

    suspend fun searchAlbums(query: String, limit: Int = 30): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while searching albums.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$baseUrl/api/album?name=$encodedQuery&_order=ASC&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseAlbumList(JSONArray(rawJson))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during album search", e)
        }
        return@withContext emptyList()
    }

    suspend fun searchArtists(query: String, limit: Int = 10): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while searching artists.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$baseUrl/api/artist?name=$encodedQuery&_order=ASC&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseArtistList(JSONArray(rawJson))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during artist search", e)
        }
        return@withContext emptyList()
    }

    suspend fun searchSongs(query: String, limit: Int = 20): List<SongSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while searching songs.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$baseUrl/api/song?title=$encodedQuery&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(rawJson)
                val results = mutableListOf<SongSearchResult>()
                val smallSize = credentialsManager.getCoverSizeSmall()

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val id = json.optString("id")
                    val coverArtId = json.optString("coverArtId", json.optString("cover_art_id", id))

                    results.add(
                        SongSearchResult(
                            id = id,
                            title = json.optString("title"),
                            artist = json.optString("artist", json.optString("artistName")),
                            artistId = json.optString("artistId", json.optString("artist_id")),
                            album = json.optString("album"),
                            albumId = json.optString("albumId", json.optString("album_id")),
                            coverArtUrl = getCoverArtUrl(coverArtId, smallSize),
                            duration = json.optInt("duration", 0)
                        )
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during song search", e)
        }
        return@withContext emptyList()
    }

    private suspend fun parseAlbumList(jsonArray: JSONArray): List<Album> {
        val albumsList = mutableListOf<Album>()
        val smallSize = credentialsManager.getCoverSizeSmall()

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)

            albumsList.add(
                Album(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    artist = json.optString("albumArtist", json.optString("artist")),
                    artistId = json.optString("albumArtistId", json.optString("artist_id")),
                    coverArtUrl = getCoverArtUrl(json.optString("id"), smallSize),
                    year = json.optInt("maxYear", json.optInt("year")).takeIf { it > 0 },
                    type = "album",
                    playCount = json.optInt("playCount").takeIf { it > 0 },
                    playDate = json.optString("playDate").takeIf { it.isNotEmpty() },
                    songCount = json.optInt("songCount").takeIf { it > 0 },
                    duration = json.optInt("duration").takeIf { it > 0 },
                    createdAt = json.optString("createdAt").takeIf { it.isNotEmpty() }
                )
            )
        }
        return albumsList
    }

    private suspend fun parseArtistList(jsonArray: JSONArray): List<Album> {
        val artistsList = mutableListOf<Album>()
        val smallSize = credentialsManager.getCoverSizeSmall()

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            val id = json.optString("id")
            val name = json.optString("name")

            artistsList.add(
                Album(
                    id = id,
                    name = name,
                    artist = name,
                    artistId = id,
                    coverArtUrl = getCoverArtUrl(id, smallSize),
                    year = null,
                    type = "artist",
                    playCount = json.optInt("playCount").takeIf { it > 0 },
                    songCount = json.optInt("songCount").takeIf { it > 0 },
                    albumCount = json.optInt("albumCount").takeIf { it > 0 }
                )
            )
        }

        return artistsList
    }

    /**
     * Fetches an album via Native API + separate song fetch.
     */
    suspend fun getAlbum(albumId: String): AlbumFetchResult = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch album metadata
            val albumJsonStr = performNativeRequest("/api/album/$albumId", "GET")
            if (albumJsonStr != null) {
                val albumJson = JSONObject(albumJsonStr)
                val albumDetails = parseAlbumMetadata(albumJson)

                // 2. Fetch tracks for this album
                val songsJsonStr = performNativeRequest("/api/song?album_id=$albumId&_end=500", "GET")
                if (songsJsonStr != null) {
                    debugJsonPayload("AlbumTracksRaw_$albumId", songsJsonStr)
                    val songsArray = JSONArray(songsJsonStr)
                    albumDetails.tracks.clear()                    // ← Fixed: use clear() + addAll
                    albumDetails.tracks.addAll(parseTracks(songsArray))
                }

                return@withContext AlbumFetchResult(
                    album = offlineLibraryManager?.cacheAlbum(albumDetails) ?: albumDetails,
                    fromOfflineCache = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch album via native network pipeline: $albumId", e)
        }

        // Fallback to offline cache
        return@withContext AlbumFetchResult(
            album = offlineLibraryManager?.getCachedAlbum(albumId),
            fromOfflineCache = true
        )
    }

    private suspend fun parseAlbumMetadata(json: JSONObject): AlbumDetails {
        val id = json.optString("id")
        val coverArtId = json.optString("coverArtId", json.optString("cover_art_id", id))

        // Support multiple album artists
        val albumArtists = mutableListOf<TrackArtist>()

        val participants = json.optJSONObject("participants")
        val albumArtistArray = participants?.optJSONArray("albumartist")

        if (albumArtistArray != null && albumArtistArray.length() > 0) {
            for (i in 0 until albumArtistArray.length()) {
                val artistJson = albumArtistArray.optJSONObject(i) ?: continue
                albumArtists.add(
                    TrackArtist(
                        id = artistJson.optString("id"),
                        name = artistJson.optString("name")
                    )
                )
            }
        } else {
            // Fallback to single artist
            val mainArtistName = json.optString("albumArtist", json.optString("artist"))
            val mainArtistId = json.optString("albumArtistId", json.optString("artistId"))
            if (mainArtistName.isNotEmpty()) {
                albumArtists.add(TrackArtist(id = mainArtistId, name = mainArtistName))
            }
        }

        val mainArtist = albumArtists.firstOrNull() ?: TrackArtist("", "")
        val largeSize = credentialsManager.getCoverSizeLarge()

        return AlbumDetails(
            id = id,
            name = json.optString("name"),
            artist = mainArtist.name,
            artistId = mainArtist.id,
            artists = albumArtists,                    // ← NEW
            year = json.optInt("maxYear", json.optInt("year", 0)).takeIf { it > 0 },
            coverArtUrl = getCoverArtUrl(coverArtId, largeSize),
            tracks = mutableListOf()
        )
    }

    private fun parseTracks(songsArray: JSONArray): List<Track> {
        val tracksList = mutableListOf<Track>()

        for (i in 0 until songsArray.length()) {
            val songJson = songsArray.optJSONObject(i) ?: continue

            val trackArtists = mutableListOf<TrackArtist>()

            // Best source: artists array
            val artistsArray = songJson.optJSONArray("artists")
                ?: songJson.optJSONObject("participants")?.optJSONArray("artist")

            if (artistsArray != null) {
                for (j in 0 until artistsArray.length()) {
                    val artistJson = artistsArray.optJSONObject(j) ?: continue
                    trackArtists.add(
                        TrackArtist(
                            id = artistJson.optString("id"),
                            name = artistJson.optString("name")
                        )
                    )
                }
            } else {
                // Fallback
                val artistName = songJson.optString("artist", songJson.optString("artistName"))
                if (artistName.isNotEmpty()) {
                    trackArtists.add(
                        TrackArtist(
                            id = songJson.optString("artistId", songJson.optString("artist_id")),
                            name = artistName
                        )
                    )
                }
            }

            tracksList.add(
                Track(
                    id = songJson.optString("id"),
                    title = songJson.optString("title"),
                    artists = trackArtists,
                    number = songJson.optInt("trackNumber", songJson.optInt("track_number", i + 1)),
                    discNumber = songJson.optInt("discNumber", songJson.optInt("disc_number", 1)),
                    duration = songJson.optInt("duration", 0)
                )
            )
        }

        // ← SORT: First by disc number, then by track number
        return tracksList.sortedWith(
            compareBy<Track> { it.discNumber }
                .thenBy { it.number }
        )
    }

    /**
     * Fetches a list of random albums from the native Navidrome API.
     */
    suspend fun getRandomAlbums(limit: Int = 20): List<Album> = withContext(Dispatchers.IO) {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Native authentication failed while fetching random albums.")
            return@withContext emptyList()
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        val smallSize = credentialsManager.getCoverSizeSmall()

        try {
            val url = URL("$baseUrl/api/album?_sort=random&_end=$limit")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("x-nd-authorization", "Bearer $nativeToken")
            urlConnection.setRequestProperty("x-nd-client-unique-id", nativeClientId)

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawJson = urlConnection.inputStream.bufferedReader().use { it.readText() }
                debugJsonPayload("NavidromeManager_Random", rawJson)

                val jsonArray = JSONArray(rawJson)
                val albumsList = mutableListOf<Album>()

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    albumsList.add(
                        Album(
                            id = json.optString("id"),
                            name = json.optString("name"),
                            artist = json.optString("albumArtist", json.optString("artist_name")),
                            artistId = json.optString("albumArtistId", json.optString("artist_id")),
                            coverArtUrl = getCoverArtUrl(json.optString("id"), smallSize),
                            year = json.optInt("maxYear", json.optInt("year")),
                            type = "album"
                        )
                    )
                }
                return@withContext albumsList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during random album fetch", e)
        }
        return@withContext emptyList()
    }

    suspend fun fetchArtist(artistId: String): ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            if (!ensureNativeAuth()) return@withContext null

            // 1. Fetch artist metadata
            val artistJsonStr = performNativeRequest("/api/artist/$artistId", "GET")
            if (artistJsonStr == null) return@withContext null

            val artistJson = JSONObject(artistJsonStr)
            val artistDetails = parseArtist(artistJson)

            // 2. Fetch albums by this artist
            val albumsJsonStr = performNativeRequest(
                "/api/album?artist_id=$artistId&_end=200&_sort=maxYear&_order=DESC",
                "GET"
            )

            if (albumsJsonStr != null) {
                val albumsArray = JSONArray(albumsJsonStr)
                artistDetails.albums.clear()
                artistDetails.albums.addAll(parseArtistAlbums(albumsArray))
            }

            return@withContext artistDetails
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist: $artistId", e)
            null
        }
    }

    private suspend fun parseArtist(json: JSONObject): ArtistDetails {
        val id = json.optString("id")
        val coverArtId = json.optString("coverArtId", json.optString("cover_art_id", id))

        return ArtistDetails(
            id = id,
            name = json.optString("name"),
            coverArtUrl = getCoverArtUrl(coverArtId),
            albums = mutableListOf()
        )
    }

    private suspend fun parseArtistAlbums(albumsArray: JSONArray): List<Album> {
        val albums = mutableListOf<Album>()

        for (i in 0 until albumsArray.length()) {
            val json = albumsArray.optJSONObject(i) ?: continue
            val smallSize = credentialsManager.getCoverSizeSmall()

            albums.add(
                Album(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    artist = json.optString("albumArtist", json.optString("artist")),
                    artistId = json.optString("albumArtistId", json.optString("artistId")),
                    coverArtUrl = getCoverArtUrl(json.optString("id"), smallSize),
                    year = json.optInt("maxYear", json.optInt("year")),
                    type = if (json.optBoolean("compilation", false)) "compilation" else "album"
                )
            )
        }
        return albums
    }

    // =========================================================================
    // CORE NETWORKING AND AUTHENTICATION WRAPPERS
    // =========================================================================

    /**
     * Pipelines an authenticated Native API request, auto-logging and validating session states.
     */
    private suspend fun performNativeRequest(endpoint: String, method: String, payload: String? = null): String? {
        if (!ensureNativeAuth()) {
            Log.e(TAG, "Aborting native execution: Authentication failed.")
            return null
        }

        val baseUrl = credentialsManager.getFullServerUrl()
        val headers = mapOf(
            "x-nd-authorization" to "Bearer $nativeToken",
            "x-nd-client-unique-id" to nativeClientId,
            "Content-Type" to "application/json"
        )

        val response = executeRequest("$baseUrl$endpoint", method, payload, headers)
        debugJsonPayload(TAG, response)
        return response
    }

    /**
     * Executes any basic HTTP transaction safely, abstracting boilerplate input/output streams.
     */
    private suspend fun executeRequest(
        fullUrl: String,
        method: String,
        payload: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(fullUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10000
                readTimeout = 15000
                doInput = true

                // Inject custom headers map
                headers.forEach { (key, value) -> setRequestProperty(key, value) }

                // Inject Body if present
                if (payload != null && (method == "POST" || method == "PUT")) {
                    doOutput = true
                    outputStream.use { os ->
                        os.write(payload.toByteArray(Charsets.UTF_8))
                    }
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorMsg = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.e(TAG, "HTTP Error Status $responseCode executing $method to $fullUrl: $errorMsg")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network transport failure during request connection processing", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Logic for native token persistence/initialization flow.
     */
    private suspend fun ensureNativeAuth(): Boolean = withContext(Dispatchers.IO) {
        if (nativeToken != null) return@withContext true

        val baseUrl = credentialsManager.getFullServerUrl()
        val user = credentialsManager.getUsername()
        val pass = credentialsManager.getPassword()
        if (baseUrl.isBlank() || user.isBlank() || pass.isBlank()) return@withContext false

        val authPayload = JSONObject().apply {
            put("username", user)
            put("password", pass)
        }.toString()

        val response = executeRequest(
            fullUrl = "$baseUrl/auth/login",
            method = "POST",
            payload = authPayload,
            headers = mapOf("Content-Type" to "application/json")
        )

        if (response != null) {
            try {
                val json = JSONObject(response)
                nativeToken = json.optString("token", "")
                return@withContext nativeToken != null
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing Native API JWT Authentication payload", e)
            }
        }
        return@withContext false
    }

    /**
     * Builds standard URL query parameters for Subsonic compatibility endpoints.
     */
    private suspend fun buildSubsonicAuthParams(explicitUser: String? = null, explicitPass: String? = null): String {
        val user = explicitUser ?: credentialsManager.getUsername()
        val pass = explicitPass ?: credentialsManager.getPassword()
        val salt = generateSalt()
        val token = md5(pass + salt)
        return "u=$user&t=$token&s=$salt&$API_PARAMS"
    }

    private     fun generateSalt(): String = (1..6).map {
        (('A'..'Z') + ('a'..'z') + ('0'..'9')).random()
    }.joinToString("")

    suspend fun starTrack(trackId: String): Boolean {
        val baseUrl = credentialsManager.getFullServerUrl()
        val authParams = buildSubsonicAuthParams()
        val url = "$baseUrl/rest/star?$authParams&id=$trackId&f=json"
        val response = executeRequest(url, "GET")
        return response != null
    }

    suspend fun unstarTrack(trackId: String): Boolean {
        val baseUrl = credentialsManager.getFullServerUrl()
        val authParams = buildSubsonicAuthParams()
        val url = "$baseUrl/rest/unstar?$authParams&id=$trackId&f=json"
        val response = executeRequest(url, "GET")
        return response != null
    }

    suspend fun getStarredTrackIds(): List<String> = withContext(Dispatchers.IO) {
        val baseUrl = credentialsManager.getFullServerUrl()
        val authParams = buildSubsonicAuthParams()
        val url = "$baseUrl/rest/getStarred2?$authParams&f=json"
        try {
            val response = executeRequest(url, "GET") ?: return@withContext emptyList()
            val json = JSONObject(response)
            val starred = json.optJSONObject("subsonic-response")?.optJSONObject("starred2")
            val songs = starred?.optJSONArray("song") ?: return@withContext emptyList()
            (0 until songs.length()).map { i ->
                songs.getJSONObject(i).optString("id")
            }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch starred tracks", e)
            emptyList()
        }
    }

    // =========================================================================
    // JSON PARSING LOGIC
    // =========================================================================

    /**
     * Maps the native response into your application's defined [AlbumDetails] entity structure.
     */
    private suspend fun parseAlbumDetails(json: JSONObject): AlbumDetails {
        val id = json.optString("id")
        val coverArtId = json.optString("cover_art_id", id)  // or "art" / "coverArtId" depending on version

        // Top-level album artist (preferred)
        val albumArtistId = json.optString("albumArtistId", json.optString("album_artist_id"))
        val albumArtistName = json.optString("albumArtist", json.optString("album_artist", json.optString("artist_name")))

        val tracksList = mutableListOf<Track>()

        // Navidrome native API typically uses "songs"
        val songsArray = json.optJSONArray("songs")
            ?: json.optJSONArray("tracks")
            ?: JSONArray()  // fallback

        for (i in 0 until songsArray.length()) {
            val songJson = songsArray.optJSONObject(i) ?: continue

            val trackArtists = mutableListOf<TrackArtist>()

            // Primary: full artists array (most common in recent Navidrome)
            val artistsArray = songJson.optJSONArray("artists")
            if (artistsArray != null && artistsArray.length() > 0) {
                for (j in 0 until artistsArray.length()) {
                    val artistJson = artistsArray.optJSONObject(j) ?: continue
                    trackArtists.add(
                        TrackArtist(
                            id = artistJson.optString("id"),
                            name = artistJson.optString("name")
                        )
                    )
                }
            } else {
                // Fallbacks
                val artistId = songJson.optString("artistId", songJson.optString("artist_id"))
                val artistName = songJson.optString("artist", songJson.optString("artist_name"))
                if (artistName.isNotEmpty()) {
                    trackArtists.add(TrackArtist(id = artistId, name = artistName))
                }
            }

            tracksList.add(
                Track(
                    id = songJson.optString("id"),
                    title = songJson.optString("title"),
                    artists = trackArtists,
                    number = songJson.optInt("trackNumber", songJson.optInt("track_number", songJson.optInt("number", i + 1))),
                    discNumber = songJson.optInt("discNumber", songJson.optInt("disc_number", 1)),
                    duration = songJson.optInt("duration", 0)  // usually in seconds
                )
            )
        }

        return AlbumDetails(
            id = id,
            name = json.optString("name"),
            artist = albumArtistName,           // ← Fixed for album artist
            artistId = albumArtistId,
            year = if (json.has("year") || json.has("maxYear")) {
                json.optInt("year", json.optInt("maxYear"))
            } else null,
            coverArtUrl = getCoverArtUrl(coverArtId),
            tracks = tracksList
        )
    }
}

// =========================================================================
// GLOBAL LOGGER HELPERS
// =========================================================================

fun debugJsonPayload(tag: String, rawJson: String?) {
    if (rawJson.isNullOrBlank()) {
        Log.w(tag, "Incoming JSON payload is completely empty or null.")
        return
    }

    val trimmed = rawJson.trim()
    try {
        val prettyPrintedJson = if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toString(4)
        } else if (trimmed.startsWith("{")) {
            JSONObject(trimmed).toString(4)
        } else {
            "Not a valid JSON format. Raw text:\n$trimmed"
        }

        val maxLogSize = 3000
        var i = 0
        Log.d(tag, "╔═════════════════════ RECEIVING INCOMING JSON ═════════════════════╗")
        while (i < prettyPrintedJson.length) {
            val end = minOf(i + maxLogSize, prettyPrintedJson.length)
            Log.d(tag, prettyPrintedJson.substring(i, end))
            i += maxLogSize
        }
        Log.d(tag, "╚═══════════════════════════════════════════════════════════════════╝")
    } catch (e: Exception) {
        Log.e(tag, "Failed to print structured JSON payload diagnostic", e)
    }
}
