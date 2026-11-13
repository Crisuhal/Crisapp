package ar.com.crismultimedia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player

class RadioService : Service() {
    companion object {
        const val ACTION_PLAY = "ar.com.crismultimedia.PLAY"
        const val ACTION_STOP = "ar.com.crismultimedia.STOP"
        const val ACTION_TOGGLE = "ar.com.crismultimedia.TOGGLE"
        const val EXTRA_URL = "stream_url"
        const val EXTRA_TITLE = "stream_title"
        const val EXTRA_CARD = "card_id"
        private const val CHANNEL_ID = "cris_radio_playback"
        private const val NOTIFICATION_ID = 7401
    }

    private var player: ExoPlayer? = null
    private var session: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle = "CrisMultimedia"
    private var currentUrl = ""
    private var currentCard = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "CrisMultimediaRadio").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player?.play() }
                override fun onPause() { player?.pause() }
                override fun onStop() { stopSelfAndPlayback() }
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    MediaButtonReceiver.handleIntent(this@RadioService.session, mediaButtonEvent)
                    return true
                }
            })
            isActive = true
        }
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                    .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                    .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            setWakeMode(com.google.android.exoplayer2.C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlaybackState(); updateNotification() }
                override fun onPlayerError(error: PlaybackException) { updatePlaybackState(); updateNotification() }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (!isValidStream(url)) { stopSelf(startId); return START_NOT_STICKY }
                currentUrl = url
                currentTitle = intent.getStringExtra(EXTRA_TITLE)?.take(120) ?: "Radio"
                currentCard = intent.getStringExtra(EXTRA_CARD)?.take(30) ?: ""
                startForeground(NOTIFICATION_ID, buildNotification())
                acquireWakeLock()
                player?.apply {
                    stop()
                    clearMediaItems()
                    setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)))
                    prepare()
                    playWhenReady = true
                }
            }
            ACTION_TOGGLE -> if (player?.isPlaying == true) player?.pause() else player?.play()
            ACTION_STOP -> stopSelfAndPlayback()
        }
        return START_NOT_STICKY
    }

    private fun isValidStream(url: String): Boolean = try {
        val u = Uri.parse(url)
        (u.scheme == "https" || u.scheme == "http") && !u.host.isNullOrBlank()
    } catch (_: Exception) { false }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Radio en reproducción", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controles de reproducción de CrisMultimedia"
                setShowBadge(false)
            })
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, RadioService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, requestCode, i, flags)
    }

    private fun buildNotification(): Notification {
        val isPlaying = player?.isPlaying ?: true
        val playPause = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val toggleLabel = if (isPlaying) "Pausar" else "Reproducir"
        val content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText("En vivo · CrisMultimedia")
            .setContentIntent(content)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Abrir", content)
            .addAction(playPause, toggleLabel, actionIntent(ACTION_TOGGLE, 1))
            .addAction(android.R.drawable.ic_media_rew, "Detener", actionIntent(ACTION_STOP, 2))
            .setStyle(MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(1, 2))
            .build()
    }

    private fun updateNotification() {
        if (currentUrl.isNotEmpty()) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun updatePlaybackState() {
        val playing = player?.isPlaying == true
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session?.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "CrisMultimedia · Radio en vivo").build())
        session?.setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP).setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f).build())
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (wakeLock?.isHeld != true) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CrisMultimedia:RadioPlayback").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        }
    }

    private fun stopSelfAndPlayback() {
        player?.stop()
        player?.clearMediaItems()
        currentUrl = ""
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.isPlaying != true) stopSelfAndPlayback()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        player?.release(); player = null
        session?.isActive = false; session?.release(); session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
