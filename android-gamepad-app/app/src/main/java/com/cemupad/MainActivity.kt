package com.cemupad

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cemupad.audio.AudioStreamReceiver
import com.cemupad.audio.MicBlowDetector
import com.cemupad.config.AppSettingsCodec
import com.cemupad.config.DisplaySettings
import com.cemupad.dsu.DSUServer
import com.cemupad.input.GamepadInputHandler
import com.cemupad.input.MotionHandler
import com.cemupad.input.RumbleHandler
import com.cemupad.input.TouchInputHandler
import com.cemupad.network.DiscoveryClient
import com.cemupad.network.DiscoveredServer
import com.cemupad.theme.CemuPadTheme
import com.cemupad.ui.main.MainScreen
import com.cemupad.util.Logger
import com.cemupad.video.UdpVideoReceiver
import com.cemupad.video.VideoDecoder
import com.cemupad.video.VideoStreamClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private lateinit var rumbleHandler: RumbleHandler
    private lateinit var micBlowDetector: MicBlowDetector

    private var audioReceiver: AudioStreamReceiver? = null
    private var discoveryClient: DiscoveryClient? = null
    private val discoveredServer = mutableStateOf<DiscoveredServer?>(null)

    private var videoDecoder: VideoDecoder? = null
    private var videoClient: VideoStreamClient? = null
    private var udpReceiver: UdpVideoReceiver? = null
    private val udpActive = AtomicBoolean(false)
    private val lastFedPtsUs = AtomicLong(-1L)
    private var activeSurface: Surface? = null
    private var lastKnownClientIp: String? = null

    private val isVideoStreaming = mutableStateOf(false)
    private val videoFps = mutableFloatStateOf(0f)
    private val displaySettings = mutableStateOf(DisplaySettings())
    private var wifiLock: WifiManager.WifiLock? = null

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Logger.i("MainActivity", "RECORD_AUDIO granted, starting mic blow detector")
            if (::micBlowDetector.isInitialized) {
                micBlowDetector.start()
            }
        } else {
            Logger.w("MainActivity", "RECORD_AUDIO denied, mic blow detector in manual-only mode")
        }
    }

    // Watchdog: worker loops must never die silently. If a DSU or video
    // thread died while supposed to run (e.g. an uncaught throwable),
    // restart it so Cemu restarts are picked up without reopening the app.
    // Also covers the stable deadlock where DSU stays subscribed but no
    // video client exists (nothing would otherwise recreate it).
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
                if (::dsuServer.isInitialized) {
                    dsuServer.ensureThreads()
                    val peerIp = dsuServer.activeClientAddress?.address?.hostAddress
                        ?.takeIf { it != "127.0.0.1" }
                        ?: lastKnownClientIp
                    if (peerIp != null && videoClient == null) {
                        Logger.i("MainActivity", "Watchdog: DSU subscribed but no video client; starting video to $peerIp")
                        startVideoStream(peerIp)
                    }
                }
                videoClient?.restartIfStalled()
                udpReceiver?.restartIfStalled()
            } catch (t: Throwable) {
                // A watchdog must never die: log and continue watching.
                Logger.w("MainActivity", "Watchdog pass failed: ${t.message}")
            }
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private val debugInputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: return
            if (action == "com.cemupad.INJECT_INPUT") {
                val button = intent.getStringExtra("button")
                val isDown = if (intent.hasExtra("down")) intent.getBooleanExtra("down", false) else null
                val durationMs = intent.getLongExtra("duration", 150L)

                if (button != null && ::gamepadHandler.isInitialized) {
                    val keyCode = when (button.uppercase()) {
                        "A" -> gamepadHandler.profile.keyA
                        "B" -> gamepadHandler.profile.keyB
                        "X" -> gamepadHandler.profile.keyX
                        "Y" -> gamepadHandler.profile.keyY
                        "L" -> gamepadHandler.profile.keyL
                        "R" -> gamepadHandler.profile.keyR
                        "ZL" -> gamepadHandler.profile.keyZL
                        "ZR" -> gamepadHandler.profile.keyZR
                        "PLUS", "START" -> gamepadHandler.profile.keyPlus
                        "MINUS", "SELECT" -> gamepadHandler.profile.keyMinus
                        "HOME", "MODE" -> gamepadHandler.profile.keyHome
                        "L3" -> gamepadHandler.profile.keyL3
                        "R3" -> gamepadHandler.profile.keyR3
                        "UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> null
                    }
                    if (keyCode != null) {
                        Logger.i("MainActivity", "Debug inject button $button ($keyCode), down=$isDown")
                        if (isDown != null) {
                            gamepadHandler.setVirtualButton(keyCode, isDown)
                        } else {
                            gamepadHandler.setVirtualButton(keyCode, true)
                            Handler(Looper.getMainLooper()).postDelayed({
                                gamepadHandler.setVirtualButton(keyCode, false)
                            }, durationMs)
                        }
                    }
                }

                if (::gamepadHandler.isInitialized && (intent.hasExtra("stickL_x") || intent.hasExtra("stickL_y"))) {
                    val sx = intent.getFloatExtra("stickL_x", 0f)
                    val sy = intent.getFloatExtra("stickL_y", 0f)
                    gamepadHandler.setVirtualStick(isLeftStick = true, normX = sx, normY = sy)
                }

                if (::gamepadHandler.isInitialized && (intent.hasExtra("stickR_x") || intent.hasExtra("stickR_y"))) {
                    val sx = intent.getFloatExtra("stickR_x", 0f)
                    val sy = intent.getFloatExtra("stickR_y", 0f)
                    gamepadHandler.setVirtualStick(isLeftStick = false, normX = sx, normY = sy)
                }

                if (::dsuServer.isInitialized && intent.hasExtra("touch_x") && intent.hasExtra("touch_y")) {
                    val tx = intent.getFloatExtra("touch_x", 0f).coerceIn(0f, 1919f).toInt().toShort()
                    val ty = intent.getFloatExtra("touch_y", 0f).coerceIn(0f, 941f).toInt().toShort()
                    val touchDown = if (intent.hasExtra("touch_down")) intent.getBooleanExtra("touch_down", false) else null
                    if (touchDown != null) {
                        dsuServer.updateState { state ->
                            state.touchButton = touchDown
                            state.touch1 = com.cemupad.dsu.DSUPacket.TouchPointData(active = touchDown, id = 1, x = tx, y = ty)
                        }
                    } else {
                        dsuServer.updateState { state ->
                            state.touchButton = true
                            state.touch1 = com.cemupad.dsu.DSUPacket.TouchPointData(active = true, id = 1, x = tx, y = ty)
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            dsuServer.updateState { state ->
                                state.touchButton = false
                                state.touch1 = com.cemupad.dsu.DSUPacket.TouchPointData(active = false)
                            }
                        }, durationMs)
                    }
                }

                if (intent.hasExtra("mic") && ::micBlowDetector.isInitialized) {
                    val isBlowing = intent.getBooleanExtra("mic", false)
                    micBlowDetector.setManualBlow(isBlowing)
                }
            }
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
                prefs.getBoolean(AppSettingsCodec.KEY_LIMIT_30_FPS, false)
            } else {
                null
            },
            showVirtualControls = if (prefs.contains(AppSettingsCodec.KEY_VIRTUAL_CONTROLS)) {
                prefs.getBoolean(AppSettingsCodec.KEY_VIRTUAL_CONTROLS, false)
            } else {
                null
            },
            virtualControlsOpacity = if (prefs.contains(AppSettingsCodec.KEY_VIRTUAL_CONTROLS_OPACITY)) {
                prefs.getFloat(AppSettingsCodec.KEY_VIRTUAL_CONTROLS_OPACITY, 0.5f)
            } else {
                null
            },
            audioEnabled = if (prefs.contains(AppSettingsCodec.KEY_AUDIO_ENABLED)) {
                prefs.getBoolean(AppSettingsCodec.KEY_AUDIO_ENABLED, true)
            } else {
                null
            },
            audioVolume = if (prefs.contains(AppSettingsCodec.KEY_AUDIO_VOLUME)) {
                prefs.getFloat(AppSettingsCodec.KEY_AUDIO_VOLUME, 1.0f)
            } else {
                null
            },
            vibrationEnabled = if (prefs.contains(AppSettingsCodec.KEY_VIBRATION_ENABLED)) {
                prefs.getBoolean(AppSettingsCodec.KEY_VIBRATION_ENABLED, true)
            } else {
                null
            },
            stickDeadzone = if (prefs.contains(AppSettingsCodec.KEY_STICK_DEADZONE)) {
                prefs.getFloat(AppSettingsCodec.KEY_STICK_DEADZONE, 0.08f)
            } else {
                null
            },
            micEnabled = if (prefs.contains(AppSettingsCodec.KEY_MIC_ENABLED)) {
                prefs.getBoolean(AppSettingsCodec.KEY_MIC_ENABLED, true)
            } else {
                null
            }
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize DSU server, haptics, mic, and input handlers
        dsuServer = DSUServer()
        gamepadHandler = GamepadInputHandler(dsuServer)
        touchHandler = TouchInputHandler(dsuServer)
        motionHandler = MotionHandler(this, dsuServer)
        rumbleHandler = RumbleHandler(this).apply {
            isEnabled = displaySettings.value.vibrationEnabled
        }
        micBlowDetector = MicBlowDetector(this) { isBlowing ->
            videoClient?.sendMicBlow(isBlowing)
        }

        discoveryClient = DiscoveryClient { server ->
            discoveredServer.value = server
            if (lastKnownClientIp.isNullOrEmpty() && !isVideoStreaming.value) {
                Logger.i("MainActivity", "Auto-connecting to discovered Cemu at ${server.ip}")
                lastKnownClientIp = server.ip
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LAST_CEMU_IP, server.ip)
                    .apply()
                runOnUiThread {
                    startVideoStream(server.ip)
                }
            }
        }

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
            // lastKnownClientIp is intentionally preserved across disconnects
            // so a resumed app can reconnect without waiting for a new DSU packet.
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
                        gamepadHandler = gamepadHandler,
                        discoveredServer = discoveredServer.value,
                        onConnectToServer = { serverIp ->
                            lastKnownClientIp = serverIp
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putString(PREF_LAST_CEMU_IP, serverIp)
                                .apply()
                            startVideoStream(serverIp)
                        },
                        onMicBlowChanged = { isBlowing ->
                            micBlowDetector.setManualBlow(isBlowing)
                        },
                        isVideoStreaming = isVideoStreaming.value,
                        videoFps = videoFps.floatValue,
                        displaySettings = displaySettings.value,
                        onDisplaySettingsChanged = { newSettings ->
                            displaySettings.value = newSettings
                            videoDecoder?.maxFps = if (newSettings.limitTo30Fps) 30 else 60
                            audioReceiver?.isMuted = !newSettings.audioEnabled
                            audioReceiver?.volume = newSettings.audioVolume
                            rumbleHandler.isEnabled = newSettings.vibrationEnabled
                            gamepadHandler.deadzone = newSettings.stickDeadzone
                            if (newSettings.micEnabled) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    micBlowDetector.start()
                                } else {
                                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                micBlowDetector.stop()
                                videoClient?.sendMicBlow(false)
                            }
                            persistDisplaySettings(newSettings)
                        },
                        onCalibrateGyro = {
                            if (::motionHandler.isInitialized) {
                                motionHandler.calibrateGyro()
                            }
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
        gamepadHandler.deadzone = displaySettings.value.stickDeadzone
        if (displaySettings.value.micEnabled) {
            micBlowDetector.start()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            micBlowDetector.stop()
        }
        audioReceiver = AudioStreamReceiver().apply {
            isMuted = !displaySettings.value.audioEnabled
            volume = displaySettings.value.audioVolume
            start()
        }
        rumbleHandler.isEnabled = displaySettings.value.vibrationEnabled
        discoveryClient?.start()
        startUdpReceiver()
        val filter = IntentFilter("com.cemupad.INJECT_INPUT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugInputReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(debugInputReceiver, filter)
        }
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
        try {
            unregisterReceiver(debugInputReceiver)
        } catch (_: Exception) {}
        watchdogHandler.removeCallbacks(watchdogRunnable)
        discoveryClient?.stop()
        micBlowDetector.stop()
        audioReceiver?.stop()
        audioReceiver = null
        rumbleHandler.cancel()
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
            .putBoolean(AppSettingsCodec.KEY_VIRTUAL_CONTROLS, encoded.showVirtualControls)
            .putFloat(AppSettingsCodec.KEY_VIRTUAL_CONTROLS_OPACITY, encoded.virtualControlsOpacity)
            .putBoolean(AppSettingsCodec.KEY_AUDIO_ENABLED, encoded.audioEnabled)
            .putFloat(AppSettingsCodec.KEY_AUDIO_VOLUME, encoded.audioVolume)
            .putBoolean(AppSettingsCodec.KEY_VIBRATION_ENABLED, encoded.vibrationEnabled)
            .putFloat(AppSettingsCodec.KEY_STICK_DEADZONE, encoded.stickDeadzone)
            .putBoolean(AppSettingsCodec.KEY_MIC_ENABLED, encoded.micEnabled)
            .apply()
    }

    private fun handleSurfaceAvailable(surface: Surface) {
        activeSurface = surface
        val decoder = VideoDecoder(surface, onRequestIDR = { videoClient?.requestIDR() })
        decoder.maxFps = if (displaySettings.value.limitTo30Fps) 30 else 60
        if (decoder.init()) {
            videoDecoder = decoder
            videoClient?.requestIDR()
        }
    }

    private fun handleSurfaceDestroyed() {
        videoDecoder?.release()
        videoDecoder = null
        activeSurface = null
    }

    private fun handleVideoFrame(nalData: ByteArray, ptsUs: Long) {
        // Drop exact duplicates (dual-transport overlap during switch-over);
        // legitimate streams never repeat a PTS back-to-back.
        if (ptsUs == lastFedPtsUs.getAndSet(ptsUs)) return
        videoDecoder?.decodeFrame(nalData, ptsUs)
        isVideoStreaming.value = true
        videoDecoder?.let { videoFps.floatValue = it.currentFps }
    }

    private fun startUdpReceiver() {
        if (udpReceiver != null) return
        val receiver = UdpVideoReceiver(
            onFrameReceived = { nalData, ptsUs ->
                if (!udpActive.getAndSet(true)) {
                    // First ordered frame of a UDP burst: sync point, and
                    // the TCP connection becomes control-only from here.
                    videoClient?.idleControlMode = true
                    videoClient?.requestIDR()
                }
                handleVideoFrame(nalData, ptsUs)
            }
        )
        receiver.onRequestIdr = {
            videoClient?.requestIDR()
        }
        receiver.onUdpSilence = {
            // UDP went quiet (lossy path or dead sender): fall back to TCP
            // video and stay there until the next reconnect.
            udpReceiver?.isExpectingUdp = false
            udpActive.set(false)
            Logger.w("MainActivity", "UDP video silent, falling back to TCP")
            videoClient?.idleControlMode = false
            videoClient?.requestTransport(false)
        }
        udpReceiver = receiver
        receiver.start()
        if (videoClient?.isConnected == true) {
            receiver.resetStream()
            videoClient?.requestIDR()
        }
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
        udpReceiver?.isExpectingUdp = false

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
                discoveryClient?.stop()
                udpReceiver?.resetStream()
                idleControlMode = true
                requestTransport(true)
                requestIDR()
            }
            onDisconnected = {
                Logger.i("MainActivity", "Video stream disconnected")
                isVideoStreaming.value = false
                idleControlMode = false
                rumbleHandler.cancel()
                discoveryClient?.start()
            }
            onRumbleReceived = { active, intensity, durationMs ->
                if (active) {
                    rumbleHandler.rumble(intensity, durationMs.toLong())
                } else {
                    rumbleHandler.cancel()
                }
            }
            onError = { err ->
                Logger.w("MainActivity", "Video stream error: ${err.message}")
            }
        }
        videoClient = client
        client.start()
    }

    private fun stopVideoStream() {
        videoClient?.idleControlMode = false
        videoClient?.stop()
        videoClient = null
        rumbleHandler.cancel()
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
