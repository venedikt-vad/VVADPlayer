package com.vvad.vp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DownloadManager {

    private var initialized = false
    private lateinit var offlineLibraryManager: OfflineLibraryManager
    private lateinit var navidromeManager: NavidromeManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingQueue = mutableListOf<DownloadJob>()
    private var activeDownloadJob: Job? = null
    private var currentAlbumId: String? = null

    private val _queueState = MutableStateFlow(QueueState(emptyList(), null))
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    fun init(olm: OfflineLibraryManager, nm: NavidromeManager) {
        offlineLibraryManager = olm
        navidromeManager = nm
        initialized = true
    }

    fun enqueue(albumId: String, albumName: String, action: DownloadAction) {
        if (!initialized) return

        val alreadyInQueue = _queueState.value.queue.any { it.albumId == albumId }
        val alreadyActive = _queueState.value.activeJob?.albumId == albumId
        if (alreadyInQueue || alreadyActive) return

        val job = DownloadJob(
            albumId = albumId,
            albumName = albumName,
            action = action,
            state = DownloadJobState.Queued
        )
        pendingQueue.add(job)
        _queueState.value = QueueState(pendingQueue.toList(), _queueState.value.activeJob)
        processNext()
    }

    fun cancel(albumId: String) {
        if (currentAlbumId == albumId) {
            activeDownloadJob?.cancel()
            activeDownloadJob = null
            currentAlbumId = null
        }
        pendingQueue.removeAll { it.albumId == albumId }
        val active = _queueState.value.activeJob
        _queueState.value = QueueState(
            queue = pendingQueue.toList(),
            activeJob = if (active?.albumId == albumId) null else active
        )
        processNext()
    }

    fun cancelAll() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        currentAlbumId = null
        pendingQueue.clear()
        _queueState.value = QueueState(emptyList(), null)
    }

    private fun processNext() {
        if (activeDownloadJob?.isActive == true) return
        if (pendingQueue.isEmpty()) {
            _queueState.value = QueueState(emptyList(), null)
            return
        }

        val job = pendingQueue.removeAt(0)
        _queueState.value = QueueState(pendingQueue.toList(), job)

        activeDownloadJob = scope.launch {
            currentAlbumId = job.albumId
            emitProgress(job, 0, 0, "")

            val result = runCatching {
                if (job.action == DownloadAction.RECACHE) {
                    offlineLibraryManager.clearAlbumAudioCache(job.albumId)
                    offlineLibraryManager.removeFromCache(job.albumId)
                }

                val album = withContext(Dispatchers.IO) {
                    val result = navidromeManager.getAlbum(job.albumId)
                    result.album
                } ?: throw Exception("Failed to fetch album")

                val dlResult = offlineLibraryManager.downloadAlbumForOffline(
                    details = album,
                    navidromeManager = navidromeManager,
                    onProgress = { current, total, trackName ->
                        if (isActive) emitProgress(job, current, total, trackName)
                    }
                )

                dlResult
            }

            val finalState: DownloadJobState = when {
                !isActive -> DownloadJobState.Cancelled
                result.isFailure -> DownloadJobState.Failed(
                    result.exceptionOrNull()?.localizedMessage ?: "Download failed"
                )
                else -> DownloadJobState.Completed
            }

            val completedJob = _queueState.value.activeJob?.copy(state = finalState)
            currentAlbumId = null
            activeDownloadJob = null
            _queueState.value = QueueState(pendingQueue.toList(), completedJob)

            if (finalState is DownloadJobState.Completed || finalState is DownloadJobState.Failed) {
                processNext()
            }
        }
    }

    private fun emitProgress(job: DownloadJob, current: Int, total: Int, trackName: String) {
        val progress = DownloadProgress(
            albumId = job.albumId,
            albumName = job.albumName,
            currentTrack = current,
            totalTracks = total,
            currentTrackName = trackName
        )
        val running = _queueState.value.activeJob?.copy(
            state = DownloadJobState.Downloading(progress)
        )
        _queueState.value = QueueState(pendingQueue.toList(), running)
    }

    fun destroy() {
        activeDownloadJob?.cancel()
        scope.cancel()
    }
}
