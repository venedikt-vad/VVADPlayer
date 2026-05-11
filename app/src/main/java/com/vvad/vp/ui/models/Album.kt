package com.vvad.vp.ui.models

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val coverArtUrl: String
)

data class AlbumDetails(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val year: Int?,
    val coverArtUrl: String,
    val tracks: List<Track>
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
