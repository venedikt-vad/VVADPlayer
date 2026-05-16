package com.vvad.vp.ui.models

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val coverArtUrl: String,
    val year: Int? = null,
    val type: String? = null
)

data class AlbumDetails(
    val id: String,
    val name: String,
    val artist: String,           // keep for backward compatibility
    val artistId: String,         // keep for backward compatibility (main artist)
    val artists: List<TrackArtist> = emptyList(),  // ← NEW: full list
    val year: Int?,
    val coverArtUrl: String,
    val tracks: MutableList<Track> = mutableListOf()
)

data class Track(
    val id: String,
    val title: String,
    val artists: List<TrackArtist>, // List for multiple artists
    val number: Int,
    val discNumber: Int = 1,
    val duration: Int
)

data class TrackArtist(
    val id: String,
    val name: String
)

data class ArtistDetails(
    val id: String,
    val name: String,
    val coverArtUrl: String,
    val albums: MutableList<Album> = mutableListOf()
)