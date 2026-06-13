package com.vvad.vp.data

sealed interface DownloadJobState {
    data object Queued : DownloadJobState
    data class Downloading(val progress: DownloadProgress) : DownloadJobState
    data object Completed : DownloadJobState
    data class Failed(val error: String) : DownloadJobState
    data object Cancelled : DownloadJobState
}

data class DownloadProgress(
    val albumId: String,
    val albumName: String,
    val currentTrack: Int,
    val totalTracks: Int,
    val currentTrackName: String
)

enum class DownloadAction { DOWNLOAD, RECACHE }

data class DownloadJob(
    val albumId: String,
    val albumName: String,
    val action: DownloadAction,
    val state: DownloadJobState
)

data class QueueState(
    val queue: List<DownloadJob>,
    val activeJob: DownloadJob?
) {
    val isActive: Boolean get() = activeJob != null
}
