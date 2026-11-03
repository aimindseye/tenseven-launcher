package io.github.aimindseye.tenseven.platform

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.frames.CapturePolicy

/** One explicitly approved sharing session. Stop, lock, death and revocation discard all pixels. */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastFrame = -1000L
    private var receiverRegistered = false
    private var stopped = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (stopped || width < 1 || height < 1) return
            runCatching { resize(width, height) }.onFailure { fail() }
        }
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { stopSelf() }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val consent = intent?.getParcelableExtra<Intent>(CONSENT)
        if (consent == null) { stopSelf(); return START_NOT_STICKY }
        try {
            startSharingNotification()
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, consent)
            projection!!.registerCallback(callback, handler)
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            resize(bounds.width(), bounds.height())
            val output = reader!!
            display = projection!!.createVirtualDisplay("TenSeven shared preview", output.width, output.height,
                resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                output.surface, null, handler)
            ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            ScreenCaptureState.started()
        } catch (_: RuntimeException) { fail() }
        return START_NOT_STICKY
    }

    private fun startSharingNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Screen sharing", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ScreenCaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val home = PendingIntent.getActivity(this, 0, Intent(this, SableStartActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("TenSeven screen sharing")
            .setContentText("Preview kept in memory. Tap Stop to clear it.")
            .setContentIntent(home).setOngoing(true).addAction(0, "Stop", stop).build()
        startForeground(710, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun resize(width: Int, height: Int) {
        val (w, h) = CapturePolicy.size(width, height)
        if (reader?.width == w && reader?.height == h) return
        val old = reader
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).also { output ->
            output.setOnImageAvailableListener({ source -> readFrame(source) }, handler)
        }
        display?.surface = reader!!.surface
        display?.resize(w, h, resources.configuration.densityDpi)
        old?.close()
    }

    private fun readFrame(source: ImageReader) {
        try {
            source.acquireLatestImage()?.use { image ->
                val now = SystemClock.elapsedRealtime()
                val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                if (locked) { stopSelf(); return@use }
                if (stopped || !CapturePolicy.accept(ScreenCaptureState.homeVisible, locked, now, lastFrame)) return@use
                val plane = image.planes[0]
                val paddedWidth = plane.rowStride / plane.pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val thumbnail = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                if (thumbnail !== padded) padded.recycle()
                ScreenCaptureState.preview(thumbnail)
                lastFrame = now
            }
        } catch (_: RuntimeException) { fail() }
    }

    private fun fail() { ScreenCaptureState.stopped(error = true); stopSelf() }

    override fun onDestroy() {
        stopped = true
        if (receiverRegistered) unregisterReceiver(screenOff)
        projection?.unregisterCallback(callback)
        display?.release()
        reader?.close()
        projection?.stop()
        projection = null
        ScreenCaptureState.stopped(error = ScreenCaptureState.state.value.error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CONSENT = "screen_capture_consent"
        private const val STOP = "io.github.aimindseye.tenseven.STOP_CAPTURE"
        private const val CHANNEL = "tenseven_screen_capture"
    }
}
