package com.mmax.retrocontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mmax.retrocontrol.MainActivity
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.data.FanCurvePreferences
import com.mmax.retrocontrol.data.AppProfilePreferences
import com.mmax.retrocontrol.data.ButtonLayoutProfile
import com.mmax.retrocontrol.data.ButtonLayoutProfilePreferences
import com.mmax.retrocontrol.data.ButtonLayoutTilePreferences
import com.mmax.retrocontrol.data.CpuFrequencyPolicy
import com.mmax.retrocontrol.data.FanSelectionPreferences
import com.mmax.retrocontrol.data.FanControlConfig
import com.mmax.retrocontrol.data.FanCurveSerializer
import com.mmax.retrocontrol.data.PerformanceProfilePreferences
import com.mmax.retrocontrol.data.PerformanceProfileResolver
import com.mmax.retrocontrol.data.PerformanceTilePreferences
import com.mmax.retrocontrol.data.PerformanceProfile
import com.mmax.retrocontrol.data.PresetPreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.UsbThermalFanControl
import com.mmax.retrocontrol.data.UsbThermalFanCurvePreferences
import com.mmax.retrocontrol.data.JoystickProfile
import com.mmax.retrocontrol.data.JoystickProfilePreferences
import com.mmax.retrocontrol.data.JoystickSelectionPreferences
import com.mmax.retrocontrol.data.displayName
import com.mmax.retrocontrol.hardware.FanController
import com.mmax.retrocontrol.hardware.FanResponseController
import com.mmax.retrocontrol.hardware.CpuFrequencyController
import com.mmax.retrocontrol.hardware.JoystickEffectEngine
import com.mmax.retrocontrol.data.AmbilightPreferences
import com.mmax.retrocontrol.hardware.GamepadController
import com.mmax.retrocontrol.hardware.TelemetryRepository
import com.mmax.retrocontrol.hardware.ThermalSensorReader
import com.mmax.retrocontrol.hardware.ThermalSnapshot
import com.mmax.retrocontrol.hardware.KernelFanThermalController
import com.mmax.retrocontrol.overlay.TelemetryOverlay
import com.mmax.retrocontrol.tile.FanQuickSettingsTile
import com.mmax.retrocontrol.tile.AmbilightQuickSettingsTile
import com.mmax.retrocontrol.tile.OverlayTileService
import com.mmax.retrocontrol.tile.JoystickQuickSettingsTile
import com.mmax.retrocontrol.tile.PerformanceQuickSettingsTile
import com.mmax.retrocontrol.tile.ButtonLayoutQuickSettingsTile
import com.mmax.retrocontrol.util.formatTemperature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * The foreground service owns fan, gamepad, joystick RGB, and CPU frequency-profile writes
 * plus telemetry polling.
 *
 * CPU writes are limited to cpufreq policy minimum/maximum nodes. It never changes
 * governors, GPU settings, refresh-rate settings, thermal-zone modes, or kernel
 * thermal protection.
 */
class SystemControlService : Service() {

    companion object {
        private const val TAG = "SystemControlService"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val CHANNEL_ID = "fan_control"
        private const val NOTIFICATION_ID = 1
        const val ACTION_UPDATE = "com.mmax.retrocontrol.UPDATE"
        const val ACTION_SET_PROJECTION_INTENT =
            "com.mmax.retrocontrol.SET_PROJECTION_INTENT"
        private const val ACTION_PREVIEW_JOYSTICK_PROFILE =
            "com.mmax.retrocontrol.PREVIEW_JOYSTICK_PROFILE"
        private const val ACTION_STOP_JOYSTICK_PROFILE_PREVIEW =
            "com.mmax.retrocontrol.STOP_JOYSTICK_PROFILE_PREVIEW"
        private const val ACTION_PREVIEW_FAN_PROFILE =
            "com.mmax.retrocontrol.PREVIEW_FAN_PROFILE"
        private const val ACTION_STOP_FAN_PROFILE_PREVIEW =
            "com.mmax.retrocontrol.STOP_FAN_PROFILE_PREVIEW"
        const val EXTRA_PROJECTION_INTENT = "projection_intent"
        private const val EXTRA_JOYSTICK_PROFILE_ID = "joystick_profile_id"
        private const val EXTRA_FAN_PROFILE_ID = "fan_profile_id"

        fun startOrUpdate(context: Context) {
            context.startForegroundService(
                Intent(context, SystemControlService::class.java).setAction(ACTION_UPDATE)
            )
        }

        fun previewJoystickProfile(context: Context, profileId: String) {
            context.startForegroundService(
                Intent(context, SystemControlService::class.java)
                    .setAction(ACTION_PREVIEW_JOYSTICK_PROFILE)
                    .putExtra(EXTRA_JOYSTICK_PROFILE_ID, profileId)
            )
        }

        fun stopJoystickProfilePreview(context: Context) {
            context.startForegroundService(
                Intent(context, SystemControlService::class.java)
                    .setAction(ACTION_STOP_JOYSTICK_PROFILE_PREVIEW)
            )
        }

        fun previewFanProfile(context: Context, profileId: String) {
            context.startForegroundService(
                Intent(context, SystemControlService::class.java)
                    .setAction(ACTION_PREVIEW_FAN_PROFILE)
                    .putExtra(EXTRA_FAN_PROFILE_ID, profileId)
            )
        }

        fun stopFanProfilePreview(context: Context) {
            context.startForegroundService(
                Intent(context, SystemControlService::class.java)
                    .setAction(ACTION_STOP_FAN_PROFILE_PREVIEW)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SharedPreferences

    private var fanJob: Job? = null
    private var foregroundJob: Job? = null
    private var screenOffJob: Job? = null
    private var performanceJob: Job? = null
    private var buttonLayoutJob: Job? = null
    private var profileSwitchToast: Toast? = null
    private val overlayAdjustmentMutex = Mutex()
    private var overlay: TelemetryOverlay? = null
    private lateinit var joystickEffects: JoystickEffectEngine
    private var lastNotificationUpdateMs = 0L
    private var screenReceiverRegistered = false

    @Volatile
    private var foregroundPackageName: String? = null

    @Volatile
    private var fanConfig = FanControlConfig()

    @Volatile
    private var usbThermalControl = UsbThermalFanControl()

    @Volatile
    private var frequencyPolicies = emptyList<CpuFrequencyPolicy>()

    @Volatile
    private var activePerformanceProfile: PerformanceProfile? = null

    @Volatile
    private var joystickProfile: JoystickProfile? = null

    @Volatile
    private var previewJoystickProfileId: String? = null

    @Volatile
    private var previewFanProfileId: String? = null

    @Volatile
    private var configRevision = 0L

    @Volatile
    private var overlayEnabled = false

    @Volatile
    private var fanSuspendedForScreenOff = false

    @Volatile
    private var performanceRequestInitialized = false

    @Volatile
    private var lastRequestedPerformanceProfileId: String? = null

    @Volatile
    private var buttonLayoutRequestInitialized = false

    @Volatile
    private var lastRequestedButtonLayoutProfile: ButtonLayoutProfile? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    scheduleScreenOffSuspend()
                    joystickEffects.suspendForScreenOff()
                }
                Intent.ACTION_USER_PRESENT -> {
                    resumeFanAfterUnlock()
                    joystickEffects.resumeAfterScreenOn()
                }
            }
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.FAN_MODE,
            Prefs.FAN_CURVE_CATALOG,
            Prefs.FAN_CURVE_QUIET,
            Prefs.FAN_CURVE_NORMAL,
            Prefs.FAN_CURVE_PERFORMANCE,
            Prefs.FAN_CURVE_CUSTOM,
            Prefs.LEGACY_FAN_CURVE_CUSTOM -> loadFanPreferences()
            Prefs.PRESET_CATALOG,
            Prefs.SELECTED_PRESET,
            Prefs.SELECTED_GAME_PROFILE,
            Prefs.SELECTED_NON_GAME_PROFILE,
            Prefs.APP_PROFILE_CATALOG -> {
                loadFanPreferences()
                loadJoystickPreferences()
                applyButtonLayout()
                applyPerformanceProfile()
                syncTilesToForegroundApp()
            }
            Prefs.FAN_SELECTION_SOURCE,
            Prefs.FAN_SELECTION_CURVE,
            Prefs.FAN_TILE_ENABLED -> {
                loadFanPreferences()
                FanQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.USB_THERMAL_CONTROL_ENABLED,
            Prefs.USB_THERMAL_FAN_CURVE,
            Prefs.USB_THERMAL_FAN_CURVE_DEFAULT -> loadUsbThermalPreferences()
            Prefs.THERMAL_PROTECTION_DISABLED -> applyKernelThermalPreferences()
            Prefs.BUTTON_LAYOUT_PROFILE_CATALOG -> {
                applyButtonLayout(force = true)
                ButtonLayoutQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.BUTTON_LAYOUT_TILE_PROFILE -> {
                applyButtonLayout(force = true)
                ButtonLayoutQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.PERFORMANCE_PROFILE_CATALOG -> {
                applyPerformanceProfile(force = true)
                PerformanceQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.PERFORMANCE_TILE_PROFILE -> {
                applyPerformanceProfile(force = true)
                PerformanceQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.JOYSTICK_PROFILE_CATALOG -> {
                loadJoystickPreferences()
                JoystickQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.JOYSTICK_SELECTION_SOURCE,
            Prefs.JOYSTICK_SELECTION_PROFILE,
            Prefs.JOYSTICK_TILE_ENABLED -> {
                loadJoystickPreferences(force = true)
                JoystickQuickSettingsTile.requestRefresh(applicationContext)
            }
            Prefs.AMBILIGHT_TILE_ENABLED,
            Prefs.AMBILIGHT_BRIGHTNESS -> {
                scope.launch {
                    loadJoystickPreferences()
                    AmbilightQuickSettingsTile.requestRefresh(applicationContext)
                }
            }
            Prefs.AMBILIGHT_LEFT_STICK_LAYOUT -> joystickEffects.setAmbilightLeftStickLayout(
                AmbilightPreferences.leftStickLayout(prefs)
            )
            Prefs.OVERLAY_ENABLED -> {
                loadOverlayPreference()
                applyOverlayState()
                OverlayTileService.requestRefresh(applicationContext)
            }
        }
        if (key == Prefs.FAN_MODE) FanQuickSettingsTile.requestRefresh(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        joystickEffects = JoystickEffectEngine(applicationContext, scope)
        createNotificationChannel()
        loadFanPreferences()
        loadOverlayPreference()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        screenReceiverRegistered = true

        startOrdinaryForeground()
        loadUsbThermalPreferences()
        loadJoystickPreferences()
        applyButtonLayout(force = true)
        applyPerformanceProfile(force = true)
        startForegroundAppMonitor()
        startFanLoop()
        applyKernelThermalPreferences()
        applyOverlayState()
        FanQuickSettingsTile.requestRefresh(applicationContext)
        JoystickQuickSettingsTile.requestRefresh(applicationContext)
        AmbilightQuickSettingsTile.requestRefresh(applicationContext)
        PerformanceQuickSettingsTile.requestRefresh(applicationContext)
        ButtonLayoutQuickSettingsTile.requestRefresh(applicationContext)
        OverlayTileService.requestRefresh(applicationContext)
        if (!getSystemService(PowerManager::class.java).isInteractive) {
            scheduleScreenOffSuspend()
            joystickEffects.suspendForScreenOff()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                loadFanPreferences()
                loadJoystickPreferences(force = true)
                applyButtonLayout(force = true)
                applyPerformanceProfile(force = true)
            }
            ACTION_SET_PROJECTION_INTENT -> {
                val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_PROJECTION_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_PROJECTION_INTENT)
                }
                if (token != null && AmbilightPreferences.isEnabled(prefs)) {
                    promoteForMediaProjection()
                    joystickEffects.setMediaProjectionIntent(token)
                }
            }
            ACTION_PREVIEW_JOYSTICK_PROFILE -> {
                previewJoystickProfileId = intent.getStringExtra(EXTRA_JOYSTICK_PROFILE_ID)
                loadJoystickPreferences(force = true)
            }
            ACTION_STOP_JOYSTICK_PROFILE_PREVIEW -> {
                previewJoystickProfileId = null
                loadJoystickPreferences(force = true)
            }
            ACTION_PREVIEW_FAN_PROFILE -> {
                previewFanProfileId = intent.getStringExtra(EXTRA_FAN_PROFILE_ID)
                loadFanPreferences()
            }
            ACTION_STOP_FAN_PROFILE_PREVIEW -> {
                previewFanProfileId = null
                loadFanPreferences()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        screenOffJob?.cancel()
        foregroundJob?.cancel()
        fanJob?.cancel()
        performanceJob?.cancel()
        buttonLayoutJob?.cancel()
        profileSwitchToast?.cancel()
        profileSwitchToast = null
        overlay?.hide()
        overlay = null
        joystickEffects.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadFanPreferences() {
        val resolved = FanSelectionPreferences.resolveEffectiveConfig(
            prefs = prefs,
            suppliedFanConfig = FanCurvePreferences.load(prefs),
            foregroundPackageName = foregroundPackageName,
            foregroundIsGame = AppProfilePreferences.isGame(this, foregroundPackageName),
        )
        fanConfig = previewFanProfileId
            ?.takeIf { resolved.catalog.profile(it) != null }
            ?.let { resolved.copy(activeProfileId = it) }
            ?: resolved
        configRevision++
    }

    private fun loadUsbThermalPreferences() {
        usbThermalControl = UsbThermalFanCurvePreferences.load(prefs)
        configRevision++
    }

    private fun loadJoystickPreferences(force: Boolean = false) {
        joystickEffects.setAmbilightLeftStickLayout(
            AmbilightPreferences.leftStickLayout(prefs)
        )
        val catalog = JoystickProfilePreferences.load(prefs)
        joystickProfile = AmbilightPreferences.takeIf { it.isEnabled(prefs) }?.profile(prefs)
            ?: previewJoystickProfileId?.let(catalog::profile)
            ?: JoystickProfilePreferences.resolveEffectiveProfile(
                prefs = prefs,
                foregroundPackageName = foregroundPackageName,
                foregroundIsGame = AppProfilePreferences.isGame(this, foregroundPackageName),
            )
        joystickEffects.apply(joystickProfile, force = force)
    }

    private fun loadOverlayPreference() {
        overlayEnabled = prefs.getBoolean(Prefs.OVERLAY_ENABLED, false)
    }

    private fun applyKernelThermalPreferences() {
        val protectionDisabled = prefs.getBoolean(Prefs.THERMAL_PROTECTION_DISABLED, false)
        scope.launch {
            KernelFanThermalController.apply(
                prefs = prefs,
                disableThermalProtection = protectionDisabled,
            )
        }
    }

    private fun applyButtonLayout(force: Boolean = false) {
        buttonLayoutJob?.cancel()
        buttonLayoutJob = scope.launch {
            val target = ButtonLayoutProfilePreferences.resolveEffectiveProfile(
                prefs = prefs,
                foregroundPackageName = foregroundPackageName,
                foregroundIsGame = AppProfilePreferences.isGame(
                    this@SystemControlService,
                    foregroundPackageName,
                ),
            )
            if (target == null) {
                buttonLayoutRequestInitialized = true
                lastRequestedButtonLayoutProfile = null
                return@launch
            }
            if (
                !force && buttonLayoutRequestInitialized &&
                target == lastRequestedButtonLayoutProfile
            ) {
                return@launch
            }
            GamepadController.applyProfile(target)
                .onSuccess { state ->
                    buttonLayoutRequestInitialized = true
                    lastRequestedButtonLayoutProfile = target
                    Log.i(TAG, "Applied button layout profile ${target.id}: $state")
                }
                .onFailure { error ->
                    buttonLayoutRequestInitialized = false
                    Log.e(TAG, "Unable to apply button layout profile ${target.id}", error)
                }
        }
    }

    private fun applyPerformanceProfile(force: Boolean = false) {
        performanceJob?.cancel()
        performanceJob = scope.launch {
            val policies = CpuFrequencyController.detectPolicies()
            frequencyPolicies = policies
            if (policies.isEmpty()) {
                activePerformanceProfile = null
                Log.w(TAG, "CPU frequency policies are unavailable")
                return@launch
            }
            val profileConfig = PerformanceProfilePreferences.load(prefs, policies)
            val performanceIds = profileConfig.profiles.mapTo(mutableSetOf()) { it.id }
            val fanIds = FanCurvePreferences.load(prefs).catalog.profiles
                .mapTo(mutableSetOf()) { it.id }
            val joystickIds = JoystickProfilePreferences.load(prefs).profiles
                .mapTo(mutableSetOf()) { it.id }
            val presetConfig = PresetPreferences.load(
                prefs,
                fanIds,
                joystickIds,
                performanceIds,
            )
            val appProfiles = AppProfilePreferences.load(
                prefs = prefs,
                availablePresetIds = presetConfig.catalog.presets
                    .mapTo(mutableSetOf()) { it.id },
                availableFanCurveIds = fanIds,
                availableJoystickProfileIds = joystickIds,
                availablePerformanceProfileIds = performanceIds,
            )
            val appIsGame = AppProfilePreferences.isGame(this@SystemControlService, foregroundPackageName)
            val appTargetId = PerformanceProfileResolver.resolveTargetProfileId(
                    profileConfig = profileConfig,
                    presetConfig = presetConfig,
                    appProfile = foregroundPackageName?.let(appProfiles::get),
                    appIsGame = appIsGame,
                )
            val targetId = if (foregroundPackageName != null) appTargetId
            else PerformanceTilePreferences.selectedProfileId(prefs, profileConfig) ?: appTargetId
            val previouslyApplied = prefs.getString(
                Prefs.LAST_APPLIED_PERFORMANCE_PROFILE,
                null,
            )
            val target = if (targetId == null) {
                if (previouslyApplied == null) {
                    performanceRequestInitialized = true
                    lastRequestedPerformanceProfileId = null
                    activePerformanceProfile = null
                    return@launch
                }
                profileConfig.stockProfile
            } else {
                profileConfig.profile(targetId)
            } ?: return@launch

            if (
                !force && performanceRequestInitialized &&
                targetId == lastRequestedPerformanceProfileId
            ) {
                activePerformanceProfile = target
                return@launch
            }

            CpuFrequencyController.applyProfile(target, policies)
                .onSuccess { result ->
                    if (!result.verificationPassed) {
                        performanceRequestInitialized = false
                        return@onSuccess
                    }
                    performanceRequestInitialized = true
                    lastRequestedPerformanceProfileId = targetId
                    activePerformanceProfile = target
                    prefs.edit {
                        if (targetId == null) {
                            remove(Prefs.LAST_APPLIED_PERFORMANCE_PROFILE)
                        } else {
                            putString(Prefs.LAST_APPLIED_PERFORMANCE_PROFILE, targetId)
                        }
                    }
                }
                .onFailure { error ->
                    performanceRequestInitialized = false
                    Log.e(TAG, "Unable to apply performance profile ${target.id}", error)
                }
        }
    }

    private fun startForegroundAppMonitor() {
        foregroundJob?.cancel()
        foregroundJob = scope.launch {
            while (isActive) {
                val foreground = ForegroundAppResolver.currentPackageName()
                if (foreground == packageName || foreground == SYSTEM_UI_PACKAGE) {
                    delay(1_000L)
                    continue
                }
                if (foreground != foregroundPackageName) {
                    foregroundPackageName = foreground
                    prefs.edit { putString(Prefs.CURRENT_FOREGROUND_APP, foreground) }
                    if (foreground.isNullOrBlank()) {
                        loadFanPreferences()
                    } else {
                        syncFanToForegroundApp(foreground)
                    }
                    loadJoystickPreferences()
                    applyButtonLayout()
                    applyPerformanceProfile()
                    syncTilesToForegroundApp()
                    showProfileSwitchToast(foreground)
                    Log.i(
                        TAG,
                        "Foreground changed: package=$foreground, " +
                            "fan=${fanConfig.activeProfile?.id ?: "off"}, " +
                            "joystick=${joystickProfile?.id ?: "off"}, " +
                            "buttons=${lastRequestedButtonLayoutProfile?.id ?: "unmanaged"}, " +
                            "performance=${lastRequestedPerformanceProfileId ?: "unmanaged"}",
                    )
                }
                delay(1_000L)
            }
        }
    }

    private fun syncFanToForegroundApp(packageName: String) {
        val resolved = FanSelectionPreferences.syncToForegroundApp(
            prefs = prefs,
            suppliedFanConfig = FanCurvePreferences.load(prefs),
            foregroundPackageName = packageName,
            foregroundIsGame = AppProfilePreferences.isGame(this, packageName),
        )
        fanConfig = previewFanProfileId
            ?.takeIf { resolved.catalog.profile(it) != null }
            ?.let { resolved.copy(activeProfileId = it) }
            ?: resolved
        configRevision++
        FanQuickSettingsTile.requestRefresh(applicationContext)
    }

    private fun syncTilesToForegroundApp() {
        val packageName = foregroundPackageName ?: return
        val appIsGame = AppProfilePreferences.isGame(this, packageName)
        val joystick = JoystickProfilePreferences.resolveEffectiveProfile(prefs, packageName, appIsGame)
        if (JoystickSelectionPreferences.load(prefs, JoystickProfilePreferences.load(prefs)).enabled) {
            joystick?.let { JoystickSelectionPreferences.selectDirectProfile(prefs, it.id) }
            JoystickQuickSettingsTile.requestRefresh(applicationContext)
        }

        val button = ButtonLayoutProfilePreferences.resolveEffectiveProfile(prefs, packageName, appIsGame)
        if (button == null) ButtonLayoutTilePreferences.clearSelection(prefs)
        else ButtonLayoutTilePreferences.select(prefs, button.id)
        ButtonLayoutQuickSettingsTile.requestRefresh(applicationContext)

        val policies = CpuFrequencyController.detectPolicies()
        val performanceConfig = PerformanceProfilePreferences.load(prefs, policies)
        val performanceIds = performanceConfig.profiles.mapTo(mutableSetOf()) { it.id }
        val fanIds = FanCurvePreferences.load(prefs).catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val joystickIds = JoystickProfilePreferences.load(prefs).profiles.mapTo(mutableSetOf()) { it.id }
        val presets = PresetPreferences.load(prefs, fanIds, joystickIds, performanceIds)
        val apps = AppProfilePreferences.load(
            prefs, presets.catalog.presets.mapTo(mutableSetOf()) { it.id }, fanIds,
            joystickIds, performanceIds,
        )
        val performanceId = PerformanceProfileResolver.resolveTargetProfileId(
            performanceConfig, presets, apps[packageName], appIsGame,
        )
        if (performanceId == null) PerformanceTilePreferences.clearSelection(prefs)
        else PerformanceTilePreferences.select(prefs, performanceId)
        PerformanceQuickSettingsTile.requestRefresh(applicationContext)
    }

    private fun promoteForMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        }
    }

    /** Ordinary fan/RGB operation must not claim MediaProjection before consent. */
    private fun startOrdinaryForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun startFanLoop() {
        fanJob?.cancel()
        fanJob = scope.launch {
            val response = FanResponseController()
            val usbResponse = FanResponseController()
            var appliedRevision = Long.MIN_VALUE
            var thermal = ThermalSnapshot()
            var lastThermalReadMs = 0L
            var currentFrequencies = emptyMap<Int, Int>()

            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastThermalReadMs >= 500L) {
                    thermal = ThermalSensorReader.read()
                    currentFrequencies = CpuFrequencyController.readCurrentFrequencies(
                        frequencyPolicies.flatMap { it.cpuIds }
                    )
                    lastThermalReadMs = now
                }

                val performanceProfile = activePerformanceProfile
                TelemetryRepository.updateFrequency(
                    policies = frequencyPolicies,
                    currentFrequenciesKhz = currentFrequencies,
                    targetMaxFrequenciesKhz = performanceProfile?.maxFrequencies.orEmpty(),
                    adjustEnabled = performanceProfile != null && frequencyPolicies.isNotEmpty(),
                    activeProfileName = performanceProfile
                        ?.displayName(this@SystemControlService)
                        .orEmpty(),
                )

                val config = fanConfig
                val profile = config.activeProfile
                val revision = configRevision
                val configChanged = revision != appliedRevision
                val controlTemp = thermal.controlTempC

                val appOutput = when {
                    fanSuspendedForScreenOff || profile == null -> {
                        if (configChanged) {
                            response.resetImmediate(controlTemp, 0.0, now)
                        }
                        0.0
                    }
                    controlTemp <= 0.0 -> 0.0
                    configChanged -> {
                        val immediate = curvePercent(profile.points, controlTemp)
                        response.resetImmediate(controlTemp, immediate, now)
                    }
                    else -> response.update(controlTemp, now) { temp ->
                        curvePercent(profile.points, temp)
                    }
                }

                val usbControl = usbThermalControl
                val usbTemp = thermal.usb?.tempC ?: 0.0
                val usbOutput = when {
                    !usbControl.enabled || usbTemp <= 0.0 -> {
                        if (configChanged) usbResponse.resetImmediate(usbTemp, 0.0, now)
                        0.0
                    }
                    configChanged -> {
                        val immediate = curvePercent(usbControl.profile.points, usbTemp)
                        usbResponse.resetImmediate(usbTemp, immediate, now)
                    }
                    else -> usbResponse.update(usbTemp, now) { temp ->
                        curvePercent(usbControl.profile.points, temp)
                    }
                }
                val output = maxOf(appOutput, usbOutput)

                val profileName = profile?.displayName(this@SystemControlService).orEmpty()
                val profilePoints = profile?.points.orEmpty()
                val percent = FanController.writePercent(output)
                TelemetryRepository.updateThermal(
                    thermal = thermal,
                    fanPercent = percent,
                    fanAdjustEnabled = profile != null &&
                        !fanSuspendedForScreenOff &&
                        controlTemp > 0.0,
                    activeCurveName = profileName,
                    activeCurvePoints = profilePoints,
                )

                if (now - lastNotificationUpdateMs >= 2_000L) {
                    updateNotification()
                    lastNotificationUpdateMs = now
                }

                appliedRevision = revision
                delay(300L)
            }
        }
    }

    private fun curvePercent(
        points: List<com.mmax.retrocontrol.data.FanCurvePoint>,
        tempC: Double,
    ): Double {
        return FanCurveSerializer.interpolate(tempC, points)
    }

    private fun applyOverlayState() {
        if (overlayEnabled && android.provider.Settings.canDrawOverlays(this)) {
            if (overlay == null) {
                overlay = TelemetryOverlay(
                    context = applicationContext,
                    onAdjustFan = ::adjustActiveCurve,
                    onAdjustFrequency = ::adjustActiveFrequency,
                )
            }
            overlay?.show()
        } else {
            overlay?.hide()
            overlay = null
        }
    }

    private fun adjustActiveCurve(deltaPercent: Int) {
        scope.launch {
            overlayAdjustmentMutex.withLock {
                val profile = fanConfig.activeProfile
                val controlTemp = TelemetryRepository.state.value.thermal.controlTempC
                if (profile == null || controlTemp <= 0.0) return@withLock
                runCatching {
                    val appName = foregroundAppName() ?: return@runCatching
                    val targetId = if (profile.customName != appName) {
                        val cloned = FanCurvePreferences.add(
                            prefs = prefs,
                            name = appName,
                            templatePoints = profile.points,
                        )
                        val clonedId = cloned.catalog.profiles.last().id
                        bindFanCurveToForegroundApp(clonedId, cloned)
                        clonedId
                    } else {
                        profile.id
                    }
                    val updated = FanCurvePreferences.adjustAroundTemperature(
                        prefs = prefs,
                        profileId = targetId,
                        tempC = controlTemp,
                        deltaPercent = deltaPercent,
                    )
                    fanConfig = updated.copy(activeProfileId = targetId)
                    configRevision++
                }.onFailure { error ->
                    Log.e(TAG, "Unable to adjust the overlay fan curve", error)
                }
            }
        }
    }

    private fun adjustActiveFrequency(policyId: Int, direction: Int) {
        scope.launch {
            overlayAdjustmentMutex.withLock {
                val policies = frequencyPolicies
                val policy = policies.firstOrNull { it.id == policyId } ?: return@withLock
                val steps = policy.supportedFrequencies
                if (steps.size < 2 || direction == 0) return@withLock
                val currentProfile = activePerformanceProfile ?: return@withLock
                runCatching {
                    val appName = foregroundAppName() ?: return@runCatching
                    val target = if (
                        (!currentProfile.isEditable || currentProfile.customName != appName)
                    ) {
                        val (config, id) = PerformanceProfilePreferences.addFromTemplate(
                            prefs = prefs,
                            policies = policies,
                            name = appName,
                            maxFrequencies = currentProfile.maxFrequencies,
                        ) ?: return@runCatching
                        bindPerformanceProfileToForegroundApp(
                            id,
                            config.profiles.map { it.id }.toSet(),
                        )
                        config.profile(id) ?: return@runCatching
                    } else {
                        currentProfile
                    }
                    val currentTarget = target.maxFrequencies[policyId]
                        ?: policy.currentMaxFrequency
                    val index = steps.indices.minByOrNull { stepIndex ->
                        abs(steps[stepIndex].toLong() - currentTarget.toLong())
                    } ?: return@runCatching
                    val nextIndex = (index + direction.sign()).coerceIn(steps.indices)
                    if (nextIndex == index) return@runCatching
                    val updatedConfig = PerformanceProfilePreferences.update(
                        prefs = prefs,
                        policies = policies,
                        profileId = target.id,
                        name = target.customName.orEmpty(),
                        maxFrequencies = target.maxFrequencies + (policyId to steps[nextIndex]),
                    )
                    activePerformanceProfile = updatedConfig.profile(target.id)
                    applyPerformanceProfile(force = true)
                }.onFailure { error ->
                    Log.e(TAG, "Unable to adjust the overlay frequency profile", error)
                }
            }
        }
    }

    private fun bindFanCurveToForegroundApp(
        profileId: String,
        config: FanControlConfig,
    ) {
        val packageName = foregroundPackageName ?: return
        val fanIds = config.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val joystickIds = JoystickProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id }
        val presetConfig = PresetPreferences.load(prefs, fanIds, joystickIds)
        AppProfilePreferences.setFanCurve(
            prefs = prefs,
            packageName = packageName,
            fanCurveId = profileId,
            availablePresetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanIds,
            availableJoystickProfileIds = joystickIds,
        )
        FanSelectionPreferences.selectFollowPreset(prefs)
    }

    private fun bindPerformanceProfileToForegroundApp(
        profileId: String,
        performanceIds: Set<String>,
    ) {
        val packageName = foregroundPackageName ?: return
        val fanIds = FanCurvePreferences.load(prefs).catalog.profiles
            .mapTo(mutableSetOf()) { it.id }
        val joystickIds = JoystickProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id }
        val presetConfig = PresetPreferences.load(
            prefs,
            fanIds,
            joystickIds,
            performanceIds,
        )
        AppProfilePreferences.setPerformanceProfile(
            prefs = prefs,
            packageName = packageName,
            performanceProfileId = profileId,
            availablePresetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanIds,
            availableJoystickProfileIds = joystickIds,
            availablePerformanceProfileIds = performanceIds,
        )
        PerformanceTilePreferences.clearSelection(prefs)
    }

    private fun foregroundAppName(): String? {
        val packageName = foregroundPackageName ?: return null
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName).trim().take(40).ifBlank { null }
    }

    private fun Int.sign(): Int = if (this < 0) -1 else 1

    private fun scheduleScreenOffSuspend() {
        screenOffJob?.cancel()
        screenOffJob = scope.launch {
            delay(5_000L)
            if (!fanSuspendedForScreenOff) {
                fanSuspendedForScreenOff = true
                configRevision++
            }
        }
    }

    private fun resumeFanAfterUnlock() {
        screenOffJob?.cancel()
        screenOffJob = null
        if (fanSuspendedForScreenOff) {
            fanSuspendedForScreenOff = false
            configRevision++
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun showProfileSwitchToast(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        if (!prefs.getBoolean(Prefs.PROFILE_SWITCH_TOASTS_ENABLED, false)) return

        val fanCatalog = FanCurvePreferences.load(prefs).catalog
        val joystickCatalog = JoystickProfilePreferences.load(prefs)
        val buttonLayoutCatalog = ButtonLayoutProfilePreferences.load(prefs)
        val performanceConfig = PerformanceProfilePreferences.load(
            prefs,
            CpuFrequencyController.detectPolicies(),
        )
        val presetConfig = PresetPreferences.load(
            prefs = prefs,
            availableFanCurveIds = fanCatalog.profiles.mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = joystickCatalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availablePerformanceProfileIds = performanceConfig.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableButtonLayoutProfileIds = buttonLayoutCatalog.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        val appProfiles = AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presetConfig.catalog.presets
                .mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanCatalog.profiles.mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = joystickCatalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availablePerformanceProfileIds = performanceConfig.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableButtonLayoutProfileIds = buttonLayoutCatalog.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        val appProfile = appProfiles[packageName]
        val appIsGame = AppProfilePreferences.isGame(this, packageName)
        val effectivePreset = AppProfilePreferences.effectivePreset(
            appProfile,
            presetConfig,
            appIsGame,
        )
        val hasCustomControl = appProfile?.let {
            it.fanCurveId != null ||
                it.joystickId != null ||
                it.buttonLayoutId != null ||
                it.performanceProfileId != null
        } == true
        val customAppsOnly = prefs.getBoolean(
            Prefs.PROFILE_SWITCH_TOASTS_CUSTOM_APPS_ONLY,
            true,
        )
        if (customAppsOnly && !appIsGame && effectivePreset.isDefault && !hasCustomControl) {
            return
        }

        val appName = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        scope.launch(Dispatchers.Main) {
            profileSwitchToast?.cancel()
            profileSwitchToast = Toast.makeText(
                applicationContext,
                getString(R.string.profile_switch_toast_message, appName),
                Toast.LENGTH_SHORT,
            ).also(Toast::show)
        }
    }

    private fun buildNotification(): Notification {
        val telemetry = TelemetryRepository.state.value
        val profile = fanConfig.activeProfile
        val cpu = telemetry.thermal.cpuSummary
        val gpu = telemetry.thermal.gpuSummary
        val cpuText = if (cpu.count > 0) formatTemperature(cpu.averageC)
            else getString(R.string.not_available)
        val gpuText = if (gpu.count > 0) formatTemperature(gpu.averageC)
            else getString(R.string.not_available)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fan)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    R.string.notification_content,
                    profile?.displayName(this) ?: getString(R.string.fan_mode_off),
                    telemetry.fanPercent,
                    cpuText,
                    gpuText,
                )
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }
}
