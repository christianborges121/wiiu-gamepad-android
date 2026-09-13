package com.cemupad

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cemupad.config.AppSettingsCodec
import com.cemupad.config.DisplaySettings
import com.cemupad.dsu.DSUServer
import com.cemupad.input.GamepadInputHandler
import com.cemupad.input.MotionHandler
import com.cemupad.input.TouchInputHandler
import com.cemupad.theme.CemuPadTheme
import com.cemupad.ui.main.MainScreen
import com.cemupad.util.Logger
import com.cemupad.video.UdpVideoReceiver
import com.cemupad.video.VideoDecoder
import com.cemupad.video.VideoStreamClient
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "cemupad_connection"
        private const val PREF_LAST_CEMU_IP = "last_cemu_ip"
        private const val WATCHDOG_INTERVAL_MS = 5000L
    }

    private lateinit var dsuServer: DSUServer
    private lateinit var gamepadHandler: GamepadInputHandler
    private lateinit var touchHandler: TouchInputHandler
    private lateinit var motionHandler: MotionHandler

    private var videoDecoder: VideoDecoder? = null
    private var videoClient: VideoStreamClient? = null
    private var udpReceiver: UdpVideoReceiver? = null
    private val udpActive = AtomicBoolean(false)
    private var activeSurface: Surface? = null
    private var lastKnownClientIp: String? = null

    private val isVideoStreaming = mutableStateOf(false)
    private val videoFps = mutableFloatStateOf(0f)
    private val displaySettings = mutableStateOf(DisplaySettings())

    private var wifiLock: WifiManager.WifiLock? = null

    // Watchdog: worker loops must never die silently. If a DSU or video
    // thread died while supposed to run (e.g. an uncaught throwable),
    // restart it so Cemu restarts are picked up without reopening the app.
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
                if (::dsuServer.isInitialized) {
                    dsuServer.ensureThreads()
                }
                videoClient?.restartIfStalled()
                udpReceiver?.restartIfStalled()
            } catch (_: Exception) {
            }
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Preserve the last verified peer across activity/process restarts so video
        // reconnect does not depend on Cemu sending a fresh DSU packet first.
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lastKnownClientIp = prefs.getString(PREF_LAST_CEMU_IP, null)
        displaySettings.value = AppSettingsCodec.decode(
            fitModeName = prefs.getString(AppSettingsCodec.KEY_FIT_MODE, null),
            resolutionName = prefs.getString(AppSettingsCodec.KEY_RESOLUTION, null),
            diagnosticsOverlayEnabled = if (prefs.contains(AppSettingsCodec.KEY_DIAGNOSTICS_OVERLAY)) {
                prefs.getBoolean(AppSettingsCodec.KEY_DIAGNOSTICS_OVERLAY, false)
            } else {
                null
            },
            connectionHelpVisible = if (prefs.contains(AppSettingsCodec.KEY_CONNECTION_HELP)) {
                prefs.getBoolean(AppSettingsCodec.KEY_CONNECTION_HELP, true)
            } else {
                null
            },
            limitTo30Fps = if (prefs.contains(AppSettingsCodec.KEY_LIMIT_30_FPS)) {
                prefs.getBoolean(AppSettingsCodec.KEY_LIMIT_30_FPS, true)
            } else {
                null
            }
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize DSU server and input handlers
        dsuServer = DSUServer()
        gamepadHandler = GamepadInputHandler(dsuServer)
        touchHandler = TouchInputHandler(dsuServer)
        motionHandler = MotionHandler(this, dsuServer)

        // Hook automatic video stream connection when a Cemu client connects
        dsuServer.onClientConnected = { clientAddr ->
            val clientIp = clientAddr.address.hostAddress ?: ""
            if (clientIp.isNotEmpty() && clientIp != "127.0.0.1") {
                lastKnownClientIp = clientIp
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LAST_CEMU_IP, clientIp)
                    .apply()
                startVideoStream(clientIp)
            }
        }

        dsuServer.onClientDisconnected = {
            // Keep the most recent client IP so a resumed app can reconnect without waiting for a new DSU packet.
            if (dsuServer.activeClientAddress == null) {
                lastKnownClientIp = lastKnownClientIp
            }
            stopVideoStream()
        }

        updateDisplayRotation()

        setContent {
            CemuPadTheme {
                ComposeSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        dsuServer = dsuServer,
                        touchHandler = touchHandler,
                        isVideoStreaming = isVideoStreaming.value,
                        videoFps = videoFps.floatValue,
                        displaySettings = displaySettings.value,
                        onDisplaySettingsChanged = { newSettings ->
                            displaySettings.value = newSettings
                            videoDecoder?.maxFps = if (newSettings.limitTo30Fps) 30 else 60
                            persistDisplaySettings(newSettings)
                        },
                        onSurfaceAvailable = { surface -> handleSurfaceAvailable(surface) },
                        onSurfaceDestroyed = { handleSurfaceDestroyed() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        acquireWifiLock()
        dsuServer.start()
        motionHandler.start()
        startUdpReceiver()
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    override fun onResume() {
        super.onResume()
        updateDisplayRotation()

        val reconnectIp = dsuServer.activeClientAddress?.address?.hostAddress
            ?: lastKnownClientIp

        if (!reconnectIp.isNullOrEmpty() && reconnectIp != "127.0.0.1") {
            startVideoStream(reconnectIp)
        }
    }

    override fun onStop() {
        super.onStop()
        watchdogHandler.removeCallbacks(watchdogRunnable)
        stopUdpReceiver()
        stopVideoStream()
        handleSurfaceDestroyed()
        motionHandler.stop()
        dsuServer.stop()
        releaseWifiLock()
    }

    private fun persistDisplaySettings(settings: DisplaySettings) {
        val encoded = AppSettingsCodec.encode(settings)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(AppSettingsCodec.KEY_FIT_MODE, encoded.fitModeName)
            .putString(AppSettingsCodec.KEY_RESOLUTION, encoded.resolutionName)
            .putBoolean(AppSettingsCodec.KEY_DIAGNOSTICS_OVERLAY, encoded.diagnosticsOverlayEnabled)
            .putBoolean(AppSettingsCodec.KEY_CONNECTION_HELP, encoded.connectionHelpVisible)
            .putBoolean(AppSettingsCodec.KEY_LIMIT_30_FPS, encoded.limitTo30Fps)
            .apply()
    }

    private fun handleSurfaceAvailable(surface: Surface) {
        activeSurface = surface
        val decoder = VideoDecoder(surface, onRequestIDR = { videoClient?.requestIDR() })
        decoder.maxFps = if (displaySettings.value.limitTo30Fps) 30 else 60
        if (decoder.init()) {
            videoDecoder = decoder
        }
    }

    private fun handleSurfaceDestroyed() {
        videoDecoder?.release()
        videoDecoder = null
        activeSurface = null
    }

    private fun handleVideoFrame(nalData: ByteArray, ptsUs: Long) {
        videoDecoder?.decodeFrame(nalData, ptsUs)
        isVideoStreaming.value = true
        videoDecoder?.let { videoFps.floatValue = it.currentFps }
    }

    private fun startUdpReceiver() {
        if (udpReceiver != null) return
        val receiver = UdpVideoReceiver(
            onFrameReceived = { nalData, ptsUs ->
                udpActive.set(true)
                handleVideoFrame(nalData, ptsUs)
            }
        )
        receiver.onUdpSilence = {
            // UDP went quiet (lossy path or dead sender): fall back to TCP
            // video and stay there until the next reconnect.
            if (udpActive.getAndSet(false)) {
                Logger.w("MainActivity", "UDP video silent, falling back to TCP")
                videoClient?.requestTransport(false)
            }
        }
        udpReceiver = receiver
        receiver.start()
    }

    private fun stopUdpReceiver() {
        udpReceiver?.stop()
        udpReceiver = null
        udpActive.set(false)
    }

    private fun startVideoStream(host: String) {
        if (videoClient?.isConnected == true && videoClient?.host == host) return
        stopVideoStream()
        udpActive.set(false)

        Logger.i("MainActivity", "Connecting to Cemu video stream at $host:26761")
        val client = VideoStreamClient(
            host = host,
            port = VideoStreamClient.DEFAULT_PORT,
            onFrameReceived = { nalData, ptsUs ->
                if (!udpActive.get()) {
                    handleVideoFrame(nalData, ptsUs)
                }
            }
        ).apply {
            onConnected = {
                Logger.i("MainActivity", "Video stream connected to $host:26761")
                isVideoStreaming.value = true
                requestTransport(true)
            }
            onDisconnected = {
                Logger.i("MainActivity", "Video stream disconnected")
                isVideoStreaming.value = false
            }
            onError = { err ->
                Logger.w("MainActivity", "Video stream error: ${err.message}")
            }
        }
        videoClient = client
        client.start()
    }

    private fun stopVideoStream() {
        videoClient?.stop()
        videoClient = null
        isVideoStreaming.value = false
        videoFps.floatValue = 0f
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::gamepadHandler.isInitialized && gamepadHandler.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::gamepadHandler.isInitialized && gamepadHandler.onGenericMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::gamepadHandler.isInitialized && gamepadHandler.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (::gamepadHandler.isInitialized && gamepadHandler.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (::gamepadHandler.isInitialized && gamepadHandler.onGenericMotionEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    @Suppress("DEPRECATION")
    private fun updateDisplayRotation() {
        if (!::motionHandler.isInitialized) return
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_90
        } else {
            windowManager.defaultDisplay.rotation
        }
        motionHandler.displayRotation = rotation
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CemuPad:WifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }
}
