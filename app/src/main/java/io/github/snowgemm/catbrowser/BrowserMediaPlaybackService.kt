package io.github.snowgemm.catbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession as PlatformMediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.MediaSession as GeckoMediaSession

/**
 * Bridges GeckoView's per-tab media session to Android system media controls.
 *
 * GeckoView remains responsible for decoding and playback. This service only
 * publishes metadata, transport controls, foreground playback state and focus.
 */
class BrowserMediaPlaybackService : Service() {
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var platformSession: PlatformMediaSession
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var isForeground = false
    private var isStopping = false
    private var isActive = false
    private var isPlaying = false
    private var mediaTitle = "Cat Browser"
    private var mediaSubtitle = "Đang phát nội dung web"

    private val audioFocusListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            val controller = browserController()
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    controller?.notifyMediaAudioFocusChange(GeckoMediaSession.SYSTEM_AUDIO_FOCUS_GAIN)
                }

                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    controller?.notifyMediaAudioFocusChange(
                        GeckoMediaSession.SYSTEM_AUDIO_FOCUS_PERMANENT_LOSS,
                    )
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    controller?.notifyMediaAudioFocusChange(
                        GeckoMediaSession.SYSTEM_AUDIO_FOCUS_TRANSIENT_LOSS,
                    )
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()
        platformSession =
            PlatformMediaSession(this, MEDIA_SESSION_TAG).apply {
                setCallback(
                    object : PlatformMediaSession.Callback() {
                        override fun onPlay() {
                            browserController()?.resumeMediaPlayback()
                        }

                        override fun onPause() {
                            browserController()?.pauseMediaPlayback()
                        }

                        override fun onStop() {
                            browserController()?.stopMediaPlayback()
                        }

                        override fun onSeekTo(position: Long) {
                            browserController()?.seekMediaPlayback(position)
                        }
                    },
                )
                setFlags(
                    PlatformMediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        PlatformMediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
                )
                isActive = true
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_UPDATE -> handleUpdate(intent)
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> browserController()?.pauseMediaPlayback()
            ACTION_STOP -> {
                if (!isStopping) browserController()?.stopMediaPlayback()
                stopPlayback()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleUpdate(intent: Intent) {
        isActive = intent.getBooleanExtra(EXTRA_ACTIVE, false)
        if (!isActive) {
            stopPlayback()
            return
        }

        mediaTitle = intent.getStringExtra(EXTRA_TITLE)?.take(MAX_METADATA_LENGTH) ?: "Cat Browser"
        mediaSubtitle = intent.getStringExtra(EXTRA_SUBTITLE)?.take(MAX_METADATA_LENGTH).orEmpty()
        isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
        updatePlatformSession()

        val notification = buildNotification()
        if (isPlaying) {
            if (!startAsForeground(notification)) {
                browserController()?.pauseMediaPlayback()
                stopPlayback()
                return
            }
            requestAudioFocus()
        } else {
            abandonAudioFocus()
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun handlePlay() {
        isActive = true
        isPlaying = true
        updatePlatformSession()
        if (!startAsForeground(buildNotification())) {
            stopPlayback()
            return
        }
        requestAudioFocus()
        browserController()?.resumeMediaPlayback()
    }

    private fun startAsForeground(notification: Notification): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        }.isSuccess

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val request =
            audioFocusRequest
                ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                    .also { audioFocusRequest = it }

        when (audioManager.requestAudioFocus(request)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasAudioFocus = true
                browserController()?.notifyMediaAudioFocusChange(GeckoMediaSession.SYSTEM_AUDIO_FOCUS_GAIN)
            }

            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                browserController()?.notifyMediaAudioFocusChange(
                    GeckoMediaSession.SYSTEM_AUDIO_FOCUS_TRANSIENT_LOSS,
                )
            }

            else -> browserController()?.pauseMediaPlayback()
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        hasAudioFocus = false
    }

    private fun updatePlatformSession() {
        platformSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, mediaSubtitle)
                .build(),
        )
        platformSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO,
                )
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (isPlaying) 1f else 0f,
                )
                .build(),
        )
        platformSession.isActive = isActive
    }

    private fun buildNotification(): Notification {
        val action =
            if (isPlaying) {
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.media_pause),
                    servicePendingIntent(ACTION_PAUSE, REQUEST_PAUSE),
                ).build()
            } else {
                Notification.Action.Builder(
                    android.R.drawable.ic_media_play,
                    getString(R.string.media_play),
                    servicePendingIntent(ACTION_PLAY, REQUEST_PLAY),
                ).build()
            }
        val openIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentIntent =
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(mediaTitle)
            .setContentText(mediaSubtitle)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(action)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(platformSession.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .build()
    }

    private fun servicePendingIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, BrowserMediaPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.media_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun browserController() =
        (application as? CatBrowserApplication)?.existingBrowserController()

    private fun stopPlayback() {
        if (isStopping) return
        isStopping = true
        isActive = false
        isPlaying = false
        abandonAudioFocus()
        platformSession.isActive = false
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        browserController()?.stopMediaPlayback()
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        abandonAudioFocus()
        platformSession.isActive = false
        platformSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "cat_browser_media"
        private const val MEDIA_SESSION_TAG = "CatBrowserMediaSession"
        private const val NOTIFICATION_ID = 4701
        private const val MAX_METADATA_LENGTH = 160
        private const val REQUEST_OPEN = 4702
        private const val REQUEST_PLAY = 4703
        private const val REQUEST_PAUSE = 4704

        private const val ACTION_UPDATE = "io.github.snowgemm.catbrowser.media.UPDATE"
        private const val ACTION_PLAY = "io.github.snowgemm.catbrowser.media.PLAY"
        private const val ACTION_PAUSE = "io.github.snowgemm.catbrowser.media.PAUSE"
        private const val ACTION_STOP = "io.github.snowgemm.catbrowser.media.STOP"

        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"

        fun publish(
            context: android.content.Context,
            isActive: Boolean,
            isPlaying: Boolean,
            title: String,
            subtitle: String,
        ) {
            val intent =
                Intent(context, BrowserMediaPlaybackService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_ACTIVE, isActive)
                    .putExtra(EXTRA_PLAYING, isPlaying)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_SUBTITLE, subtitle)
            if (isPlaying) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, BrowserMediaPlaybackService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
