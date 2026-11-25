package com.rb.carlauncher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock

/** Follows whichever app is currently playing and exposes its metadata + transport controls. */
class MediaMonitor(
    private val ctx: Context,
    private val onChange: (MediaInfo) -> Unit
) {
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(ctx, MediaListenerService::class.java)
    private var controller: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component)
        } catch (e: SecurityException) {
            onChange(MediaInfo())
            return
        }
        refresh()
    }

    fun stop() {
        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        controller?.unregisterCallback(callback)
        controller = null
    }

    fun refresh() {
        try {
            pick(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            onChange(MediaInfo())
        }
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            onChange(MediaInfo())
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        var pos = ps?.position ?: 0L
        if (playing && ps != null) {
            pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        }
        val appName = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        } catch (e: Exception) {
            c.packageName
        }
        onChange(
            MediaInfo(
                available = title.isNotBlank() || artist.isNotBlank() || ps != null,
                title = title,
                artist = artist,
                art = art,
                playing = playing,
                positionMs = pos,
                durationMs = duration,
                stampMs = SystemClock.elapsedRealtime(),
                appName = appName
            )
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun setShuffle(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) controller?.transportControls?.setShuffleMode(if (on) 1 else 0)
    }

    fun setRepeat(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) controller?.transportControls?.setRepeatMode(if (on) 2 else 0)
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }
}
