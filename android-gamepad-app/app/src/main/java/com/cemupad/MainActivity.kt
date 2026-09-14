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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
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
import com.cemupad.audio.MicVoiceStreamer
import com.cemupad.input.CaptureEngine
import com.cemupad.input.ControllerDetector
import com.cemupad.input.DetectedProfile
import com.cemupad.input.DeviceProfileStore
import com.cemupad.input.MappableControl
import com.cemupad.ui.mapping.MappingPromptUi
import com.cemupad.ui.mapping.MappingTestRow
import com.cemupad.ui.mapping.MappingWizardActions
import com.cemupad.ui.mapping.MappingWizardScreen
import com.cemupad.config.AppSettingsCodec
import com.cemupad.config.DisplaySettings
import com.cemupad.dsu.DSUServer
import com.cemupad.input.GamepadInputHandler
import com.cemupad.input.MotionHandler
import com.cemupad.input.RumbleHandler
import com.cemupad.input.TouchInputHandler
import com.cemupad.network.DiscoveryClient
import com.cemupad.network.DiscoveryResponder
import com.cemupad.network.DiscoveredServer
import com.cemupad.theme.CemuPadTheme
import com.cemupad.ui.config.ConfigAction
import com.cemupad.ui.config.ConfigMenuState
import com.cemupad.ui.main.MainScreen
import com.cemupad.util.Logger
import com.cemupad.util.NetworkUtils
import com.cemupad.video.UdpVideoReceiver
import com.cemupad.video.VideoDecoder
import com.cemupad.video.VideoStreamClient
import android.media.MediaFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "cemupad_connection"
        private const val PREF_LAST_CEMU_IP = "last_cemu_ip"
        private const val PREF_AUTH_TOKEN = "auth_token"
        private const val WATCHDOG_INTERVAL_MS = 5000L
        // Discovery responses arrive ~every 2s while Cemu is up; three missed
        // windows means the host is gone (or stopped answering).
        private const val DISCOVERY_STALE_MS = 7000L
    }

    private lateinit var dsuServer: DSUServer
    private lateinit var gamepadHandler: GamepadInputHandler
    private lateinit var touchHandler: TouchInputHandler
    private lateinit var motionHandler: MotionHandler
    private lateinit var rumbleHandler: RumbleHandler
    private lateinit var micBlowDetector: MicBlowDetector

    private var audioReceiver: AudioStreamReceiver? = null
    private var discoveryClient: DiscoveryClient? = null
    private var discoveryResponder: DiscoveryResponder? = null
    private var micVoiceStreamer: MicVoiceStreamer? = null

    // First-connect input mapping wizard (Phase 6)
    private lateinit var deviceProfileStore: DeviceProfileStore
    private val captureEngine = CaptureEngine()
    private val promptedDescriptors = mutableSetOf<String>()
    private var lastProfileDescriptor: String? = null
    private var lastDetectedProfile: DetectedProfile? = null
    private val activeGamepadDescriptor = mutableStateOf<String?>(null)
    private val activeGamepadName = mutableStateOf<String?>(null)
    private val mappingPrompt = mutableStateOf<MappingPromptUi?>(null)
    private val wizardScreen = mutableStateOf<MappingWizardScreen?>(null)
    private val captureTick = mutableStateOf(0)
    private val captureFlash = mutableStateOf<String?>(null)
    private val configMenuState = ConfigMenuState()
    private var menuClosingKeyCode: Int? = null
    private var lastShownHatDir: String? = null
    private var lastShownStickDir: String? = null
    private val testHistoryKeys = mutableSetOf<Int>()
    private val testHistoryDirs = mutableSetOf<String>()
    private val testHistoryStickDirs = mutableSetOf<String>()

    /** Active hat D-pad directions in priority order (UP/DOWN/LEFT/RIGHT). */
    private fun activeHatDirs(hatX: Float, hatY: Float): List<String> {
        val dirs = mutableListOf<String>()
        if (hatY < -0.5f) dirs.add("UP")
        if (hatY > 0.5f) dirs.add("DOWN")
        if (hatX < -0.5f) dirs.add("LEFT")
        if (hatX > 0.5f) dirs.add("RIGHT")
        return dirs
    }
    private val discoveredServer = mutableStateOf<DiscoveredServer?>(null)
    // Last time a CEMUPAD_HERE response arrived. The found-card is cleared
    // once responses stop (e.g. Cemu closed) so it can't strand the UI.
    private var lastDiscoveryTimeMs: Long = 0L

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
    private val debugBundleForcedDiagnostics = mutableStateOf(false)
    private val choreographerPacer = com.cemupad.video.ChoreographerPacer()
    private val networkQualityTracker = com.cemupad.network.NetworkQualityTracker()
    private val telemetryHandler = Handler(Looper.getMainLooper())
    private var lastTelemetryDatagramsReceived = 0L
    private var lastTelemetryPacketsLost = 0L
    private var lastTelemetryFramesCompleted = 0L
    private var lastTelemetryFramesDropped = 0L
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
    private val telemetryRunnable = object : Runnable {
        override fun run() {
            try {
                val vc = videoClient
                val receiver = udpReceiver
                if (vc != null && vc.isConnected && receiver != null) {
                    val s = receiver.stats
                    val newReceived = s.datagramsReceived
                    val newLost = s.packetsLost
                    val newCompleted = s.framesCompleted
                    val newDropped = s.framesDropped
                    val deltaReceived = (newReceived - lastTelemetryDatagramsReceived).coerceAtLeast(0L).toInt()
                    val deltaLost = (newLost - lastTelemetryPacketsLost).coerceAtLeast(0L).toInt()
                    val deltaCompleted = (newCompleted - lastTelemetryFramesCompleted).coerceAtLeast(0L).toInt()
                    val deltaDropped = (newDropped - lastTelemetryFramesDropped).coerceAtLeast(0L).toInt()
                    if (deltaReceived + deltaLost > 0) {
                        networkQualityTracker.onPacketExpected(deltaReceived + deltaLost)
                        networkQualityTracker.onPacketReceived(deltaReceived)
                    }
                    repeat(deltaCompleted) { networkQualityTracker.onFrameEvaluated(false) }
                    repeat(deltaDropped) { networkQualityTracker.onFrameEvaluated(true) }
                    lastTelemetryDatagramsReceived = newReceived
                    lastTelemetryPacketsLost = newLost
                    lastTelemetryFramesCompleted = newCompleted
                    lastTelemetryFramesDropped = newDropped
                    val sample = networkQualityTracker.sample()
                    vc.sendStatsReport(sample.lossHundredths, sample.dropHundredths)
                    Logger.v("MainActivity", "Telemetry sent loss=${sample.lossHundredths} drop=${sample.dropHundredths} jitter=${sample.jitterMs} latency=${sample.avgLatencyMs}")
                }
            } catch (t: Throwable) {
                Logger.w("MainActivity", "Telemetry pass failed: ${t.message}")
            }
            telemetryHandler.postDelayed(this, 500)
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
                if (discoveredServer.value != null &&
                    DiscoveryClient.isStale(
                        lastDiscoveryTimeMs,
                        System.currentTimeMillis(),
                        DISCOVERY_STALE_MS
                    )
                ) {
                    Logger.i("MainActivity", "Discovery stale; clearing found-Cemu card")
                    discoveredServer.value = null
                }
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
            if (action == "com.cemupad.WIZARD_TEST") {
                val mode = intent.getStringExtra("mode") ?: "testing"
                runOnUiThread {
                    if (mode == "capturing") {
                        val target = intent.getStringExtra("target") ?: "Stick L Move"
                        wizardScreen.value = MappingWizardScreen.Capturing(
                            targetLabel = target,
                            done = intent.getIntExtra("done", 5),
                            total = intent.getIntExtra("total", 26),
                            flash = null,
                            isStickTarget = target.contains("Stick")
                        )
                    } else {
                        testHistoryKeys.clear()
                        testHistoryDirs.clear()
                        testHistoryStickDirs.clear()
                        lastShownHatDir = null
                        lastShownStickDir = null
                        val profile = if (::gamepadHandler.isInitialized) gamepadHandler.profile else com.cemupad.input.ControllerProfile.DEFAULT
                        wizardScreen.value = MappingWizardScreen.Testing(
                            profileName = "Debug Preview",
                            rows = testRowsFor(profile),
                            lastKeyCode = null
                        )
                    }
                }
                return
            }
            if (action == "com.cemupad.INJECT_INPUT") {
                if (intent.hasExtra("config_menu")) {
                    val cmd = intent.getStringExtra("config_menu")
                    if (cmd.equals("open", ignoreCase = true)) {
                        configMenuState.open()
                    } else if (cmd.equals("close", ignoreCase = true)) {
                        configMenuState.close()
                    }
                }
                val button = intent.getStringExtra("button")
                if (configMenuState.isOpen && button != null) {
                    val items = configMenuState.getItems(
                        screen = configMenuState.currentScreen,
                        settings = displaySettings.value,
                        activeControllerName = activeGamepadName.value,
                        phoneIp = NetworkUtils.getLocalIpAddress(),
                        dsuPort = if (::dsuServer.isInitialized) dsuServer.port else 26760
                    )
                    when (button.uppercase()) {
                        "A" -> configMenuState.onSelectA(items, displaySettings.value, { applyAndPersistSettings(it) }, { handleConfigAction(it) })
                        "B" -> configMenuState.onBackB()
                        "UP" -> configMenuState.onUp(items)
                        "DOWN" -> configMenuState.onDown(items)
                        "LEFT" -> configMenuState.onLeft(items, displaySettings.value, { applyAndPersistSettings(it) })
                        "RIGHT" -> configMenuState.onRight(items, displaySettings.value, { applyAndPersistSettings(it) })
                    }
                    return
                }
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
        com.cemupad.util.Logger.init(applicationContext)

        // Preserve the last verified peer across activity/process restarts so video
        // reconnect does not depend on Cemu sending a fresh DSU packet first.
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lastKnownClientIp = prefs.getString(PREF_LAST_CEMU_IP, null)
        displaySettings.value = AppSettingsCodec.decode(
            fitModeName = prefs.getString(AppSettingsCodec.KEY_FIT_MODE, null),
            resolutionName = prefs.getString(AppSettingsCodec.KEY_RESOLUTION, null),
            videoBitrateMbps = if (prefs.contains(AppSettingsCodec.KEY_VIDEO_BITRATE)) {
                prefs.getInt(AppSettingsCodec.KEY_VIDEO_BITRATE, DisplaySettings.VIDEO_BITRATE_DEFAULT_MBPS)
            } else {
                null
            },
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
            vibrationIntensity = if (prefs.contains(AppSettingsCodec.KEY_VIBRATION_INTENSITY)) {
                prefs.getFloat(AppSettingsCodec.KEY_VIBRATION_INTENSITY, 1.0f)
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
            },
            framePacingName = prefs.getString(AppSettingsCodec.KEY_FRAME_PACING, null)
        )
        choreographerPacer.start()

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
            intensityScale = displaySettings.value.vibrationIntensity
        }
        micBlowDetector = MicBlowDetector(this) { isBlowing ->
            videoClient?.sendMicBlow(isBlowing)
        }

        discoveryClient = DiscoveryClient { server ->
            discoveredServer.value = server
            lastDiscoveryTimeMs = System.currentTimeMillis()
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

        // Answer Cemu pairing-dialog probes so the PC can list this phone.
        // Runs for the whole activity lifetime (silent unless probed).
        discoveryResponder = DiscoveryResponder { senderIp ->
            Logger.i("MainActivity", "Cemu discovery probe answered for $senderIp")
        }

        deviceProfileStore = DeviceProfileStore(
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        )

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
                            applyAndPersistSettings(newSettings)
                        },
                        onPreviewVibration = { scale ->
                            rumbleHandler.preview(scale)
                        },
                        onCalibrateGyro = {
                            if (::motionHandler.isInitialized) {
                                motionHandler.calibrateGyro()
                            }
                        },
                        mappingPrompt = mappingPrompt.value,
                        onMappingSetup = {
                            mappingPrompt.value?.let { prompt ->
                                mappingPrompt.value = null
                                openWizardForDescriptor(prompt.descriptor)
                            }
                        },
                        onMappingDismiss = { mappingPrompt.value = null },
                        activeControllerName = activeGamepadName.value,
                        onOpenInputMapping = {
                            val known = activeGamepadDescriptor.value
                            if (known != null) {
                                openWizardForDescriptor(known)
                            } else {
                                // No input seen yet: scan attached devices directly.
                                val found = try {
                                    ControllerDetector.firstGamepad()
                                } catch (_: Exception) {
                                    null
                                }
                                if (found != null) {
                                    val desc = found.first.descriptor ?: ""
                                    if (desc.isEmpty()) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Controller has no descriptor",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        activeGamepadDescriptor.value = desc
                                        activeGamepadName.value = found.first.name ?: "Controller"
                                        lastDetectedProfile = found.second
                                        gamepadHandler.profile = deviceProfileStore.activeFor(
                                            desc, found.second?.profile
                                        )
                                        lastProfileDescriptor = desc
                                        openWizardForDescriptor(desc)
                                    }
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "No gamepad detected — press any controller button first",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        configMenuState = configMenuState,
                        wizardScreen = wizardScreen.value,
                        wizardActions = wizardActions(),
                        forceDiagnosticsOverlay = debugBundleForcedDiagnostics.value,
                        onSurfaceAvailable = { surface -> handleSurfaceAvailable(surface) },
                        onSurfaceDestroyed = { handleSurfaceDestroyed() },
                        onExportDebug = { exportDebugBundle() }
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
        rumbleHandler.intensityScale = displaySettings.value.vibrationIntensity
        discoveryClient?.start()
        discoveryResponder?.start()
        startUdpReceiver()
        val filter = IntentFilter("com.cemupad.INJECT_INPUT").apply { addAction("com.cemupad.WIZARD_TEST") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugInputReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(debugInputReceiver, filter)
        }
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
        networkQualityTracker.reset()
        lastTelemetryDatagramsReceived = 0L
        lastTelemetryPacketsLost = 0L
        lastTelemetryFramesCompleted = 0L
        lastTelemetryFramesDropped = 0L
        telemetryHandler.postDelayed(telemetryRunnable, 500)
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
        telemetryHandler.removeCallbacks(telemetryRunnable)
        discoveryClient?.stop()
        discoveryResponder?.stop()
        micBlowDetector.stop()
        audioReceiver?.stop()
        audioReceiver = null
        rumbleHandler.cancel()
        choreographerPacer.stop()
        stopUdpReceiver()
        stopVideoStream()
        handleSurfaceDestroyed()
        motionHandler.stop()
        dsuServer.stop()
        releaseWifiLock()
    }

    private fun applyAndPersistSettings(newSettings: DisplaySettings) {
        val oldSettings = displaySettings.value
        displaySettings.value = newSettings
        videoDecoder?.maxFps = if (newSettings.limitTo30Fps) 30 else 60
        videoDecoder?.framePacingMode = newSettings.framePacing
        audioReceiver?.isMuted = !newSettings.audioEnabled
        audioReceiver?.volume = newSettings.audioVolume
        rumbleHandler.isEnabled = newSettings.vibrationEnabled
        rumbleHandler.intensityScale = newSettings.vibrationIntensity
        gamepadHandler.deadzone = newSettings.stickDeadzone
        if (newSettings.micEnabled) {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                micBlowDetector.start()
                // (Re)start voice PCM when the toggle flips on mid-session.
                if (videoClient?.isConnected == true) {
                    val host = lastKnownClientIp
                    if (!host.isNullOrEmpty()) startVoiceStream(host)
                }
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            micBlowDetector.stop()
            videoClient?.sendMicBlow(false)
            stopVoiceStream()
        }
        // Forward dynamic encoder changes to Cemu (no-op when unchanged).
        if (newSettings.videoBitrateMbps != oldSettings.videoBitrateMbps) {
            videoClient?.sendBitrate(newSettings.videoBitrateMbps * 1_000_000)
        }
        val newPreset = newSettings.resolutionPreset
        val resolvedNew = newPreset.resolveForDevice(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
            newSettings.videoBitrateMbps
        )
        val resolvedOld = oldSettings.resolutionPreset.resolveForDevice(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
            oldSettings.videoBitrateMbps
        )
        if (resolvedNew != resolvedOld && resolvedNew.width > 0 && resolvedNew.height > 0) {
            videoClient?.sendResolution(resolvedNew.width, resolvedNew.height)
        }
        if (newSettings.videoCodec != oldSettings.videoCodec) {
            val targetMime = resolveVideoMimeType(newSettings.videoCodec)
            val useHevc = targetMime == MediaFormat.MIMETYPE_VIDEO_HEVC
            videoClient?.sendCodec(useHevc)
            activeSurface?.let { surface ->
                if (videoDecoder?.mimeType != targetMime) {
                    videoDecoder?.release()
                    val decoder = VideoDecoder(surface, onRequestIDR = { videoClient?.requestIDR() }, mimeType = targetMime)
                    decoder.maxFps = if (newSettings.limitTo30Fps) 30 else 60
                    decoder.framePacingMode = newSettings.framePacing
                    decoder.choreographerPacer = choreographerPacer
                    if (decoder.init()) {
                        videoDecoder = decoder
                        videoClient?.requestIDR()
                    }
                }
            }
        }
        persistDisplaySettings(newSettings)
    }

    private fun handleConfigAction(action: ConfigAction) {
        when (action) {
            ConfigAction.CALIBRATE_GYRO -> {
                if (::motionHandler.isInitialized) {
                    motionHandler.calibrateGyro()
                }
            }
            ConfigAction.MAP_CONTROLLER -> {
                configMenuState.close()
                val known = activeGamepadDescriptor.value
                if (known != null) {
                    openWizardForDescriptor(known)
                } else {
                    val found = try {
                        ControllerDetector.firstGamepad()
                    } catch (_: Exception) {
                        null
                    }
                    if (found != null) {
                        val desc = found.first.descriptor ?: ""
                        if (desc.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "Controller has no descriptor",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            activeGamepadDescriptor.value = desc
                            activeGamepadName.value = found.first.name ?: "Controller"
                            lastDetectedProfile = found.second
                            gamepadHandler.profile = deviceProfileStore.activeFor(
                                desc, found.second?.profile
                            )
                            lastProfileDescriptor = desc
                            openWizardForDescriptor(desc)
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "No gamepad detected — press any controller button first",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            ConfigAction.EXPORT_DEBUG_BUNDLE -> exportDebugBundle()
            ConfigAction.RESET_SETTINGS -> applyAndPersistSettings(DisplaySettings())
        }
    }

    private fun persistDisplaySettings(settings: DisplaySettings) {
        val encoded = AppSettingsCodec.encode(settings)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(AppSettingsCodec.KEY_FIT_MODE, encoded.fitModeName)
            .putString(AppSettingsCodec.KEY_RESOLUTION, encoded.resolutionName)
            .putInt(AppSettingsCodec.KEY_VIDEO_BITRATE, encoded.videoBitrateMbps)
            .putBoolean(AppSettingsCodec.KEY_DIAGNOSTICS_OVERLAY, encoded.diagnosticsOverlayEnabled)
            .putBoolean(AppSettingsCodec.KEY_CONNECTION_HELP, encoded.connectionHelpVisible)
            .putBoolean(AppSettingsCodec.KEY_LIMIT_30_FPS, encoded.limitTo30Fps)
            .putBoolean(AppSettingsCodec.KEY_VIRTUAL_CONTROLS, encoded.showVirtualControls)
            .putFloat(AppSettingsCodec.KEY_VIRTUAL_CONTROLS_OPACITY, encoded.virtualControlsOpacity)
            .putBoolean(AppSettingsCodec.KEY_AUDIO_ENABLED, encoded.audioEnabled)
            .putFloat(AppSettingsCodec.KEY_AUDIO_VOLUME, encoded.audioVolume)
            .putBoolean(AppSettingsCodec.KEY_VIBRATION_ENABLED, encoded.vibrationEnabled)
            .putFloat(AppSettingsCodec.KEY_VIBRATION_INTENSITY, encoded.vibrationIntensity)
            .putFloat(AppSettingsCodec.KEY_STICK_DEADZONE, encoded.stickDeadzone)
            .putBoolean(AppSettingsCodec.KEY_MIC_ENABLED, encoded.micEnabled)
            .putString(AppSettingsCodec.KEY_FRAME_PACING, encoded.framePacingName)
            .putString(AppSettingsCodec.KEY_VIDEO_CODEC, encoded.videoCodecName)
            .apply()
    }

    private fun resolveVideoMimeType(pref: com.cemupad.config.VideoCodecPreference): String {
        return when (pref) {
            com.cemupad.config.VideoCodecPreference.HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
            com.cemupad.config.VideoCodecPreference.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            com.cemupad.config.VideoCodecPreference.AUTO -> {
                val hasHardwareHevc = try {
                    android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                        !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) } &&
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated else true
                    }
                } catch (_: Exception) {
                    false
                }
                if (hasHardwareHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            }
        }
    }

    private fun handleSurfaceAvailable(surface: Surface) {
        activeSurface = surface
        val targetMime = resolveVideoMimeType(displaySettings.value.videoCodec)
        val decoder = VideoDecoder(surface, onRequestIDR = { videoClient?.requestIDR() }, mimeType = targetMime)
        decoder.maxFps = if (displaySettings.value.limitTo30Fps) 30 else 60
        decoder.framePacingMode = displaySettings.value.framePacing
        decoder.choreographerPacer = choreographerPacer
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
            },
            getAuthCredential = {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(PREF_AUTH_TOKEN, 0L)
            },
            onAuthToken = { token ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putLong(PREF_AUTH_TOKEN, token)
                    .apply()
            },
            onPinRequired = {
                // PIN UI disabled 2026-09-13: fail closed instead of prompting.
                // (No enable path exists server-side, so this is unreachable
                // unless a PIN-requiring build appears.)
                Logger.w("MainActivity", "Server demanded PIN; PIN UI disabled, aborting video connection")
                videoClient?.cancelAuth()
            }
        ).apply {
            onConnected = {
                Logger.i("MainActivity", "Video stream connected to $host:26761")
                isVideoStreaming.value = true
                discoveryClient?.stop()
                udpReceiver?.resetStream()
                idleControlMode = true
                requestTransport(true)
                // Sync persisted encoder settings: drawer changes only fire on
                // user edits, so a fresh connection would otherwise keep the
                // PC encoder at its 854x480/6Mbps defaults.
                videoClient?.sendBitrate(displaySettings.value.videoBitrateMbps * 1_000_000)
                val preset = displaySettings.value.resolutionPreset
                val resolvedPreset = preset.resolveForDevice(
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels,
                    displaySettings.value.videoBitrateMbps
                )
                if (resolvedPreset.width > 0 && resolvedPreset.height > 0) {
                    videoClient?.sendResolution(resolvedPreset.width, resolvedPreset.height)
                }
                val targetMime = resolveVideoMimeType(displaySettings.value.videoCodec)
                videoClient?.sendCodec(targetMime == MediaFormat.MIMETYPE_VIDEO_HEVC)
                val activeProfile = if (::gamepadHandler.isInitialized) gamepadHandler.profile else com.cemupad.input.ControllerProfile.DEFAULT
                val entries = pendingPushedEntries ?: com.cemupad.config.InputMappingCodec.toVpadEntries(activeProfile)
                videoClient?.sendPushedMappings(entries)
                pendingPushedEntries = null
                startVoiceStream(host)
                requestIDR()
            }
            onDisconnected = {
                Logger.i("MainActivity", "Video stream disconnected")
                isVideoStreaming.value = false
                idleControlMode = false
                rumbleHandler.cancel(force = true)
                discoveryClient?.start()
            }
            onRumbleReceived = { active, intensity, durationMs ->
                Logger.i("MainActivity", "Rumble packet: active=$active intensity=$intensity duration=${durationMs}ms")
                if (active) {
                    rumbleHandler.rumble(intensity, durationMs.toLong())
                } else {
                    rumbleHandler.cancel(force = false)
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
        stopVoiceStream()
        videoClient?.idleControlMode = false
        videoClient?.stop()
        videoClient = null
        rumbleHandler.cancel(force = true)
        isVideoStreaming.value = false
        videoFps.floatValue = 0f
    }

    /**
     * Starts 32 kHz voice PCM streaming to Cemu when the microphone toggle is
     * on. Requires RECORD_AUDIO (checked inside the streamer); safe no-op
     * otherwise. Runs alongside the blow detector's own recorder.
     */
    private fun startVoiceStream(host: String) {
        stopVoiceStream()
        if (!displaySettings.value.micEnabled) return
        micVoiceStreamer = MicVoiceStreamer(this, host).apply { start() }
    }

    private fun stopVoiceStream() {
        micVoiceStreamer?.stop()
        micVoiceStreamer = null
    }

    /**
     * Builds the remote-debugging bundle (log file + window screenshot +
     * device/codec report) and opens the system share sheet. Screenshot via
     * PixelCopy so SurfaceView frames are captured correctly.
     *
     * Temporarily forces diagnostics overlay on so the screenshot always
     * contains FPS / packet counters for remote triage, then restores.
     */
    private fun exportDebugBundle() {
        try {
            debugBundleForcedDiagnostics.value = true
            // Give Compose one frame to recompose with overlay visible before capture
            window.decorView.postDelayed({
                try {
                    val rootView = window.decorView.rootView
                    val width = rootView.width
                    val height = rootView.height
                    if (width <= 0 || height <= 0) {
                        Toast.makeText(this, "Screen not ready, try again", Toast.LENGTH_SHORT).show()
                        debugBundleForcedDiagnostics.value = false
                        return@postDelayed
                    }
                    Toast.makeText(this, "Capturing debug bundle…", Toast.LENGTH_SHORT).show()
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        width, height, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    android.view.PixelCopy.request(
                        window,
                        bitmap,
                        { result ->
                            // Restore overlay state immediately after capture request
                            debugBundleForcedDiagnostics.value = false
                            Thread {
                                try {
                                    val png = if (result == android.view.PixelCopy.SUCCESS) {
                                        com.cemupad.util.DebugBundle.screenshotPng(bitmap)
                                    } else {
                                        com.cemupad.util.Logger.w("DebugBundle", "Screenshot failed: $result")
                                        null
                                    }
                                    val report = com.cemupad.util.DebugBundle.collectDeviceReport(
                                        applicationContext, displaySettings.value
                                    )
                                    val reportText = com.cemupad.util.DebugBundle.formatReport(report)
                                    val logText = com.cemupad.util.Logger.logFiles()
                                        .joinToString("\n") { file ->
                                            "===== ${file.name} =====\n" + try {
                                                file.readText()
                                            } catch (_: Exception) {
                                                "<unreadable>"
                                            }
                                        }.ifEmpty { "<no log file — restart the app once>" }
                                    val dir = java.io.File(cacheDir, "debug")
                                    if (!dir.exists()) dir.mkdirs()
                                    val zip = java.io.File(dir, "cemupad-debug.zip")
                                    if (zip.exists()) zip.delete()
                                    com.cemupad.util.DebugBundle.buildZip(zip, reportText, logText, png)
                                    runOnUiThread {
                                        try {
                                            com.cemupad.util.DebugBundle.shareZip(this, zip)
                                        } catch (_: Exception) {
                                            Toast.makeText(
                                                this,
                                                "No app available to share with",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    com.cemupad.util.Logger.w("DebugBundle", "Export failed: ${e.message}")
                                    runOnUiThread {
                                        Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    try {
                                        bitmap.recycle()
                                    } catch (_: Exception) {
                                    }
                                }
                            }.apply { isDaemon = true; start() }
                        },
                        android.os.Handler(android.os.Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    debugBundleForcedDiagnostics.value = false
                    com.cemupad.util.Logger.w("DebugBundle", "Export failed: ${e.message}")
                    Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }, 150)
        } catch (e: Exception) {
            debugBundleForcedDiagnostics.value = false
            com.cemupad.util.Logger.w("DebugBundle", "Export failed: ${e.message}")
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Keys that always pass through to the system, even with the wizard open.
     * Everything else is wizard/capture input while a wizard screen shows.
     */
    private fun isSystemPassthroughKey(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER -> true
            else -> false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Wizard open: eat-first (Dolphin MotionAlertDialog rule). Every key
        // except volume/power goes to the wizard and is consumed, so nothing
        // leaks to focus traversal. Back semantics are Dolphin-style: short
        // Back is bindable input in capture mode and a no-op elsewhere; only
        // LONG-press Back exits the wizard.
        val wizard = wizardScreen.value
        if (wizard != null && !isSystemPassthroughKey(event)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Logger.i(
                    "WizardKeys",
                    "key action=${event.action} code=${event.keyCode} " +
                        "name=${KeyEvent.keyCodeToString(event.keyCode)} source=${event.source} " +
                        "screen=${wizard.javaClass.simpleName}"
                )
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.isLongPress) {
                    captureEngine.cancel()
                    wizardScreen.value = null
                    return true
                }
                if (wizard is MappingWizardScreen.Capturing) {
                    when (captureEngine.recordKey(event.keyCode)) {
                        CaptureEngine.RecordResult.Conflict ->
                            captureFlash.value = "Already assigned — press another button"
                        CaptureEngine.RecordResult.Ignored -> {}
                        else -> captureFlash.value = null
                    }
                    captureTick.value++
                    refreshCaptureScreen()
                    return true
                }
                if (wizard is MappingWizardScreen.Testing) {
                    testHistoryKeys.add(event.keyCode)
                    wizardScreen.value = wizard.copy(
                        lastKeyCode = event.keyCode,
                        lastPressedLabel = KeyEvent.keyCodeToString(event.keyCode),
                        // A key press supersedes any lingering hat/stick direction.
                        lastDpadDir = null,
                        lastStickDir = null,
                        historyKeys = testHistoryKeys.toSet(),
                        historyDirs = testHistoryDirs.toSet(),
                        historyStickDirs = testHistoryStickDirs.toSet()
                    )
                }
            } else if (wizard is MappingWizardScreen.Capturing) {
                // Swallow releases in capture mode so nothing leaks into gameplay.
                return true
            }
            return true
        }

        // New-controller banner over drawer: don't let drawer behind handle controller.
        // A = Set up (open wizard), B = Dismiss, others consumed so drawer doesn't move.
        if (mappingPrompt.value != null && !isSystemPassthroughKey(event)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        mappingPrompt.value?.let { prompt ->
                            mappingPrompt.value = null
                            openWizardForDescriptor(prompt.descriptor)
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        mappingPrompt.value = null
                        return true
                    }
                }
            }
            return true
        }

        // Swallowed trailing key release after closing menu
        if (menuClosingKeyCode != null && event.action == KeyEvent.ACTION_UP && event.keyCode == menuClosingKeyCode) {
            menuClosingKeyCode = null
            return true
        }

        // System back key delegates to super so OnBackPressedDispatcher / BackHandler handles navigation
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }

        // Intercept controller input while Configuration menu is open
        if (configMenuState.isOpen && !isSystemPassthroughKey(event)) {
            Logger.i(
                "ConfigKeys",
                "key action=${event.action} code=${event.keyCode} " +
                    "name=${KeyEvent.keyCodeToString(event.keyCode)} source=${event.source} " +
                    "screen=${configMenuState.currentScreen.name} focused=${configMenuState.focusedIndex}"
            )
            if (::gamepadHandler.isInitialized && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
                resolveGamepadProfile(event.deviceId)
            }
            val profile = if (::gamepadHandler.isInitialized) gamepadHandler.profile else null
            val items = configMenuState.getItems(
                screen = configMenuState.currentScreen,
                settings = displaySettings.value,
                activeControllerName = activeGamepadName.value,
                phoneIp = NetworkUtils.getLocalIpAddress(),
                dsuPort = if (::dsuServer.isInitialized) dsuServer.port else 26760
            )
            val wasOpen = configMenuState.isOpen
            val handled = configMenuState.handleKeyEvent(
                event = event,
                profile = profile,
                settings = displaySettings.value,
                items = items,
                onSettingsChanged = { applyAndPersistSettings(it) },
                onAction = { handleConfigAction(it) }
            )
            if (wasOpen && !configMenuState.isOpen && event.action == KeyEvent.ACTION_DOWN) {
                menuClosingKeyCode = event.keyCode
            }
            return handled
        }

        // Gamepad shortcut to open configuration menu (e.g. Menu / Guide / Select when not captured)
        if (wizardScreen.value == null && !configMenuState.isOpen && event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                configMenuState.open()
                return true
            }
        }

        if (::gamepadHandler.isInitialized) {
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                resolveGamepadProfile(event.deviceId)
            }
            if (gamepadHandler.handleKeyEvent(event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // Test screen: surface hat D-pad + stick directions (keys alone can't show them).
        // Change-gated to avoid recompose storms.
        (wizardScreen.value as? MappingWizardScreen.Testing)?.let { testing ->
            val dirs = activeHatDirs(
                event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            )
            val dir = dirs.firstOrNull()
            if (dir != null && dir != lastShownHatDir) {
                lastShownHatDir = dir
                testHistoryDirs.add(dir)
                wizardScreen.value = testing.copy(
                    lastKeyCode = null,
                    lastPressedLabel = "D-Pad ${dir.lowercase().replaceFirstChar { it.uppercase() }} (hat)",
                    lastDpadDir = dir,
                    lastStickDir = null,
                    historyKeys = testHistoryKeys.toSet(),
                    historyDirs = testHistoryDirs.toSet(),
                    historyStickDirs = testHistoryStickDirs.toSet()
                )
            } else if (dir == null) {
                lastShownHatDir = null
            }
            // Stick directions (threshold 0.5) — same Testing screen
            if (::gamepadHandler.isInitialized) {
                val p = gamepadHandler.profile
                val lx = event.getAxisValue(p.axisLX); val ly = event.getAxisValue(p.axisLY)
                val rx = event.getAxisValue(p.axisRX); val ry = event.getAxisValue(p.axisRY)
                val stickDir = when {
                    ly < -0.5f -> "L_UP"; ly > 0.5f -> "L_DOWN"; lx < -0.5f -> "L_LEFT"; lx > 0.5f -> "L_RIGHT"
                    ry < -0.5f -> "R_UP"; ry > 0.5f -> "R_DOWN"; rx < -0.5f -> "R_LEFT"; rx > 0.5f -> "R_RIGHT"
                    else -> null
                }
                if (stickDir != null && stickDir != lastShownStickDir) {
                    lastShownStickDir = stickDir
                    testHistoryStickDirs.add(stickDir)
                    val cur = wizardScreen.value as? MappingWizardScreen.Testing ?: testing
                    wizardScreen.value = cur.copy(
                        lastKeyCode = null,
                        lastPressedLabel = "Stick ${stickDir.replace("_", " ")}",
                        lastDpadDir = null,
                        lastStickDir = stickDir,
                        historyKeys = testHistoryKeys.toSet(),
                        historyDirs = testHistoryDirs.toSet(),
                        historyStickDirs = testHistoryStickDirs.toSet()
                    )
                } else if (stickDir == null) {
                    lastShownStickDir = null
                }
            }
            // Consume all motion while Testing — prevents sticks/D-pad from driving the game behind the wizard.
            return true
        }
        if (wizardScreen.value is MappingWizardScreen.Capturing) {
            val before = captureEngine.current
            // Hat first: many pads (incl. Backbone) report the D-pad only as
            // HAT_X/HAT_Y with no key events at all.
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (hatX != 0f || hatY != 0f) {
                captureEngine.recordHat(MotionEvent.AXIS_HAT_X, hatX)
                captureEngine.recordHat(MotionEvent.AXIS_HAT_Y, hatY)
            }
            val axes = stickCaptureAxes.associateWith { event.getAxisValue(it) }
            captureEngine.recordAxes(axes)
            if (captureEngine.current != before) captureFlash.value = null
            captureTick.value++
            refreshCaptureScreen()
            return true
        }
        // Banner over drawer: consume motion so drawer doesn't scroll behind prompt.
        if (mappingPrompt.value != null) {
            return true
        }
        if (configMenuState.isOpen) {
            if (::gamepadHandler.isInitialized) {
                resolveGamepadProfile(event.deviceId)
            }
            val profile = if (::gamepadHandler.isInitialized) gamepadHandler.profile else null
            val items = configMenuState.getItems(
                screen = configMenuState.currentScreen,
                settings = displaySettings.value,
                activeControllerName = activeGamepadName.value,
                phoneIp = NetworkUtils.getLocalIpAddress(),
                dsuPort = if (::dsuServer.isInitialized) dsuServer.port else 26760
            )
            return configMenuState.handleMotionEvent(
                event = event,
                profile = profile,
                settings = displaySettings.value,
                items = items,
                onSettingsChanged = { applyAndPersistSettings(it) }
            )
        }
        if (::gamepadHandler.isInitialized) {
            resolveGamepadProfile(event.deviceId)
            if (gamepadHandler.onGenericMotionEvent(event)) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Binds the first-seen gamepad to its stored/detected/default profile and
     * raises the setup banner once per unknown device per session.
     */
    private fun resolveGamepadProfile(deviceId: Int) {
        if (!::gamepadHandler.isInitialized || !::deviceProfileStore.isInitialized) return
        val device = try {
            InputDevice.getDevice(deviceId)
        } catch (_: Exception) {
            null
        } ?: return
        val sources = device.sources
        val isPad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isPad) return
        val descriptor = device.descriptor ?: return
        if (descriptor.isEmpty()) return

        activeGamepadDescriptor.value = descriptor
        activeGamepadName.value = device.name ?: "Controller"
        if (lastProfileDescriptor != descriptor) {
            lastDetectedProfile = try {
                ControllerDetector.detect(device)
            } catch (_: Exception) {
                null
            }
            val active = deviceProfileStore.activeFor(
                descriptor, lastDetectedProfile?.profile
            )
            gamepadHandler.profile = active
            lastProfileDescriptor = descriptor
            pushCemuMappings(active)
        }
        if (!deviceProfileStore.has(descriptor) && !promptedDescriptors.contains(descriptor)) {
            promptedDescriptors.add(descriptor)
            val detected = lastDetectedProfile
            mappingPrompt.value = MappingPromptUi(
                descriptor = descriptor,
                deviceName = device.name ?: "Controller",
                matchLabel = detected?.matchedOn,
                confidence = detected?.confidence
            )
        }
    }

    private fun openWizardForDescriptor(descriptor: String) {
        mappingPrompt.value = null
        val device = findDeviceByDescriptor(descriptor)
        val detected = try {
            device?.let { ControllerDetector.detect(it) }
        } catch (_: Exception) {
            null
        } ?: lastDetectedProfile?.takeIf { lastProfileDescriptor == descriptor }
        if (deviceProfileStore.has(descriptor)) {
            testHistoryKeys.clear()
            testHistoryDirs.clear()
            testHistoryStickDirs.clear()
            lastShownHatDir = null
            lastShownStickDir = null
            wizardScreen.value = MappingWizardScreen.Testing(
                profileName = deviceProfileStore.load(descriptor)?.displayName ?: "Saved layout",
                rows = testRowsFor(gamepadHandler.profile),
                lastKeyCode = null
            )
        } else {
            wizardScreen.value = MappingWizardScreen.Detected(
                deviceName = device?.name ?: activeGamepadName.value ?: "Controller",
                matchLabel = detected?.matchedOn,
                confidence = detected?.confidence
            )
        }
    }

    private fun findDeviceByDescriptor(descriptor: String): InputDevice? {
        for (id in InputDevice.getDeviceIds()) {
            try {
                val device = InputDevice.getDevice(id) ?: continue
                if (device.descriptor == descriptor) return device
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun testRowsFor(profile: com.cemupad.input.ControllerProfile): List<MappingTestRow> {
        return listOf(
            MappingTestRow("A", profile.keyA),
            MappingTestRow("B", profile.keyB),
            MappingTestRow("X", profile.keyX),
            MappingTestRow("Y", profile.keyY),
            MappingTestRow("D-Pad Up", profile.keyDpadUp, "UP"),
            MappingTestRow("D-Pad Down", profile.keyDpadDown, "DOWN"),
            MappingTestRow("D-Pad Left", profile.keyDpadLeft, "LEFT"),
            MappingTestRow("D-Pad Right", profile.keyDpadRight, "RIGHT"),
            MappingTestRow("Stick L Up", 0, null, "L_UP"),
            MappingTestRow("Stick L Down", 0, null, "L_DOWN"),
            MappingTestRow("Stick L Left", 0, null, "L_LEFT"),
            MappingTestRow("Stick L Right", 0, null, "L_RIGHT"),
            MappingTestRow("Stick R Up", 0, null, "R_UP"),
            MappingTestRow("Stick R Down", 0, null, "R_DOWN"),
            MappingTestRow("Stick R Left", 0, null, "R_LEFT"),
            MappingTestRow("Stick R Right", 0, null, "R_RIGHT"),
            MappingTestRow("L", profile.keyL),
            MappingTestRow("R", profile.keyR),
            MappingTestRow("ZL", profile.keyZL),
            MappingTestRow("ZR", profile.keyZR),
            MappingTestRow("Plus", profile.keyPlus),
            MappingTestRow("Minus", profile.keyMinus),
            MappingTestRow("Home", profile.keyHome),
            MappingTestRow("Stick L Press", profile.keyL3),
            MappingTestRow("Stick R Press", profile.keyR3)
        )
    }

    private fun startCapture() {
        captureEngine.start(base = if (::gamepadHandler.isInitialized) gamepadHandler.profile else null)
        captureFlash.value = null
        refreshCaptureScreen()
    }

    private fun refreshCaptureScreen() {
        val target = captureEngine.current
        if (target == null) {
            if (captureEngine.isFinished && !captureEngine.isCancelled) {
                val total = MappableControl.ORDER.size
                val skipped = captureEngine.skippedTargets().size
                wizardScreen.value = MappingWizardScreen.Done(
                    assigned = total - skipped,
                    skipped = skipped
                )
            }
            return
        }
        val (done, total) = captureEngine.progress
        wizardScreen.value = MappingWizardScreen.Capturing(
            targetLabel = target.label,
            done = done,
            total = total,
            flash = captureFlash.value,
            isStickTarget = target.kind == com.cemupad.input.CaptureKind.STICK
        )
    }

    private fun wizardActions() = MappingWizardActions(
        onConfirmDetected = {
            testHistoryKeys.clear()
            testHistoryDirs.clear()
            testHistoryStickDirs.clear()
            lastShownHatDir = null
            lastShownStickDir = null
            wizardScreen.value = MappingWizardScreen.Testing(
                profileName = gamepadHandler.profile.displayName,
                rows = testRowsFor(gamepadHandler.profile),
                lastKeyCode = null
            )
        },
        onRemap = { startCapture() },
        onSaveTest = {
            val descriptor = activeGamepadDescriptor.value ?: return@MappingWizardActions
            val toSave = gamepadHandler.profile.copy(deviceDescriptor = descriptor)
            deviceProfileStore.save(toSave)
            pushCemuMappings(toSave)
            wizardScreen.value = null
        },
        onWizardClose = { wizardScreen.value = null },
        onResetDefault = {
            // Scrambled-profile recovery: drop the stored mapping and fall back
            // to a fresh detected-or-default profile, applied immediately.
            val descriptor = activeGamepadDescriptor.value
            if (descriptor != null) {
                deviceProfileStore.clear(descriptor)
                val device = findDeviceByDescriptor(descriptor)
                val detected = try {
                    device?.let { ControllerDetector.detect(it) }
                } catch (_: Exception) {
                    null
                }
                lastDetectedProfile = detected
                val resetProfile = deviceProfileStore.activeFor(descriptor, detected?.profile)
                gamepadHandler.profile = resetProfile
                lastProfileDescriptor = descriptor
                pushCemuMappings(resetProfile)
            }
            wizardScreen.value = null
        },
        onCaptureSkip = {
            captureEngine.skip()
            captureFlash.value = null
            refreshCaptureScreen()
        },
        onCaptureBack = {
            captureEngine.back()
            captureFlash.value = null
            refreshCaptureScreen()
        },
        onCaptureCancel = {
            captureEngine.cancel()
            wizardScreen.value = null
        },
        onCaptureConfirmStick = {
            if (captureEngine.confirmStick() == CaptureEngine.RecordResult.Ignored) {
                captureFlash.value = "Wiggle the stick further, then confirm"
            } else {
                captureFlash.value = null
            }
            refreshCaptureScreen()
        },
        onCaptureTick = {
            if (captureEngine.checkTimeout()) {
                captureEngine.skip()
                captureFlash.value = "Skipped (no input)"
            }
            refreshCaptureScreen()
        },
        onSaveCapture = {
            val descriptor = activeGamepadDescriptor.value
            val built = captureEngine.buildProfile(gamepadHandler.profile)
            if (descriptor != null && built != null) {
                val bound = built.copy(deviceDescriptor = descriptor)
                deviceProfileStore.save(bound)
                gamepadHandler.profile = bound
                pushCemuMappings(bound)
            }
            wizardScreen.value = null
        },
        onDiscardCapture = { wizardScreen.value = null }
    )

    private var pendingPushedEntries: List<Pair<Int, Int>>? = null

    private fun pushCemuMappings(profile: com.cemupad.input.ControllerProfile) {
        val entries = com.cemupad.config.InputMappingCodec.toVpadEntries(profile)
        val vc = videoClient
        if (vc != null && vc.isConnected) {
            vc.sendPushedMappings(entries)
            com.cemupad.util.Logger.i("MainActivity", "Pushed ${entries.size} mappings to Cemu")
        } else {
            pendingPushedEntries = entries
            com.cemupad.util.Logger.i("MainActivity", "Queued ${entries.size} mappings for next Cemu connect")
        }
    }

    private val stickCaptureAxes = intArrayOf(
        MotionEvent.AXIS_X,
        MotionEvent.AXIS_Y,
        MotionEvent.AXIS_Z,
        MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_RX,
        MotionEvent.AXIS_RY,
        MotionEvent.AXIS_HAT_X,
        MotionEvent.AXIS_HAT_Y,
        MotionEvent.AXIS_BRAKE,
        MotionEvent.AXIS_GAS,
        MotionEvent.AXIS_LTRIGGER,
        MotionEvent.AXIS_RTRIGGER
    )

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
