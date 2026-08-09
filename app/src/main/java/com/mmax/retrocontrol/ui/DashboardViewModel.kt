package com.mmax.retrocontrol.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.edit
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.AppControlProfile
import com.mmax.retrocontrol.data.AppProfilePreferences
import com.mmax.retrocontrol.data.BuiltInFanCurve
import com.mmax.retrocontrol.data.ButtonLayoutProfileCatalog
import com.mmax.retrocontrol.data.ButtonLayoutProfilePreferences
import com.mmax.retrocontrol.data.FaceButtonLayout
import com.mmax.retrocontrol.data.GamepadButtonMapping
import com.mmax.retrocontrol.data.GamepadTriggerMode
import com.mmax.retrocontrol.data.ControlPresetConfig
import com.mmax.retrocontrol.data.ControlItemJson
import com.mmax.retrocontrol.data.FanControlConfig
import com.mmax.retrocontrol.data.FanCurvePoint
import com.mmax.retrocontrol.data.FanCurvePreferences
import com.mmax.retrocontrol.data.FanSelectionConfig
import com.mmax.retrocontrol.data.FanSelectionPreferences
import com.mmax.retrocontrol.data.PresetPreferences
import com.mmax.retrocontrol.data.PerformanceProfileConfig
import com.mmax.retrocontrol.data.PerformanceProfilePreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.JoystickProfileCatalog
import com.mmax.retrocontrol.data.JoystickProfilePreferences
import com.mmax.retrocontrol.feature.joystick.JoystickRgbMode
import com.mmax.retrocontrol.hardware.TelemetryRepository
import com.mmax.retrocontrol.hardware.TelemetrySnapshot
import com.mmax.retrocontrol.hardware.CpuFrequencyController
import com.mmax.retrocontrol.service.SystemControlService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val icon: Bitmap?,
    val isGame: Boolean,
    val profileSummary: String = "",
)

data class DashboardState(
    val fanConfig: FanControlConfig = FanControlConfig(),
    val presetConfig: ControlPresetConfig = ControlPresetConfig(),
    val joystickProfiles: JoystickProfileCatalog = JoystickProfileCatalog(),
    val buttonLayoutProfiles: ButtonLayoutProfileCatalog = ButtonLayoutProfileCatalog(),
    val performanceProfiles: PerformanceProfileConfig = PerformanceProfileConfig(),
    val fanSelection: FanSelectionConfig = FanSelectionConfig(
        source = com.mmax.retrocontrol.data.FanSelectionSource.FollowPreset,
        enabled = true,
    ),
    val overlayEnabled: Boolean = false,
    val autoStartEnabled: Boolean = true,
    val profileSwitchNotificationsEnabled: Boolean = true,
    val usbThermalDisabled: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val appProfiles: Map<String, AppControlProfile> = emptyMap(),
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
)

data class ControlImportResult(
    val imported: Int,
    val failed: Int,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(Prefs.FILE, Application.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadPreferences()
    }

    init {
        loadPreferences()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        viewModelScope.launch {
            TelemetryRepository.state.collectLatest { telemetry ->
                mutableState.update { it.copy(telemetry = telemetry) }
            }
        }
        viewModelScope.launch {
            RootAccessManager.hasRoot.collectLatest { granted ->
                if (granted) refreshPerformancePolicies()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val launcherApps = application.getSystemService(LauncherApps::class.java)
            val apps = runCatching {
                launcherApps.getActivityList(null, Process.myUserHandle())
                    .distinctBy { it.applicationInfo.packageName }
                    .map { activity ->
                        InstalledAppInfo(
                            label = activity.label.toString(),
                            packageName = activity.applicationInfo.packageName,
                            icon = runCatching {
                                activity.getBadgedIcon(0).toBitmap(width = 96, height = 96)
                            }.getOrNull(),
                            isGame = activity.applicationInfo.category ==
                                ApplicationInfo.CATEGORY_GAME,
                        )
                    }
                    .sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER, InstalledAppInfo::label)
                            .thenBy(InstalledAppInfo::packageName)
                    )
            }.getOrDefault(emptyList())
            mutableState.update { it.copy(installedApps = apps) }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun loadPreferences() {
        val rawFanConfig = FanCurvePreferences.load(prefs)
        val fanConfig = FanSelectionPreferences.apply(prefs, rawFanConfig)
        val curveIds = fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val joystickProfiles = JoystickProfilePreferences.load(prefs)
        val joystickIds = joystickProfiles.profiles.mapTo(mutableSetOf()) { it.id }
        val buttonLayoutProfiles = ButtonLayoutProfilePreferences.load(prefs)
        val buttonLayoutIds = buttonLayoutProfiles.profiles.mapTo(mutableSetOf()) { it.id }
        val performanceProfiles = PerformanceProfilePreferences.load(
            prefs,
            mutableState.value.performanceProfiles.policies,
        )
        val performanceIds = performanceProfiles.profiles
            .mapTo(mutableSetOf()) { it.id }
            .takeIf { performanceProfiles.policies.isNotEmpty() }
        val presetConfig = PresetPreferences.load(
            prefs,
            curveIds,
            joystickIds,
            performanceIds,
            buttonLayoutIds,
        )
        val presetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id }
        mutableState.update {
            it.copy(
                fanConfig = fanConfig,
                presetConfig = presetConfig,
                joystickProfiles = joystickProfiles,
                buttonLayoutProfiles = buttonLayoutProfiles,
                performanceProfiles = performanceProfiles,
                fanSelection = FanSelectionPreferences.load(prefs, fanConfig),
                appProfiles = AppProfilePreferences.load(
                    prefs,
                    presetIds,
                    curveIds,
                    joystickIds,
                    performanceIds,
                    buttonLayoutIds,
                ),
                overlayEnabled = prefs.getBoolean(Prefs.OVERLAY_ENABLED, false),
                autoStartEnabled = prefs.getBoolean(Prefs.AUTO_START_ENABLED, true),
                profileSwitchNotificationsEnabled = prefs.getBoolean(
                    Prefs.PROFILE_SWITCH_NOTIFICATIONS_ENABLED,
                    true,
                ),
                usbThermalDisabled = prefs.getBoolean(Prefs.USB_THERMAL_DISABLED, false),
            )
        }
    }

    fun selectFanProfile(profileId: String?) {
        val config = if (profileId == null) {
            FanCurvePreferences.select(prefs, null)
        } else {
            FanSelectionPreferences.selectDirectCurve(prefs, profileId)
        }
        mutableState.update { it.copy(fanConfig = config) }
        SystemControlService.startOrUpdate(getApplication())
    }

    fun importControlItems(jsonFiles: List<String>): ControlImportResult {
        var imported = 0
        var failed = 0
        jsonFiles.forEach { json ->
            val success = runCatching {
                when (val item = ControlItemJson.decode(json)) {
                    is ControlItemJson.Item.FanCurve -> FanCurvePreferences.addImported(
                        prefs = prefs,
                        name = item.name,
                        points = item.points,
                        defaultPoints = item.defaultPoints,
                    )
                    is ControlItemJson.Item.Joystick -> JoystickProfilePreferences.addImported(
                        prefs = prefs,
                        profile = item.value,
                    )
                    is ControlItemJson.Item.ButtonLayout -> {
                        ButtonLayoutProfilePreferences.addImported(prefs, item.value)
                    }
                    is ControlItemJson.Item.Performance -> PerformanceProfilePreferences.addImported(
                        prefs = prefs,
                        policies = mutableState.value.performanceProfiles.policies,
                        name = item.name,
                        maxFrequencies = item.maxFrequencies,
                    )
                    is ControlItemJson.Item.Preset -> {
                        val current = mutableState.value
                        var preset = item.value
                        item.fanCurve?.let { fanCurve ->
                            val config = FanCurvePreferences.addImported(
                                prefs = prefs,
                                name = fanCurve.name,
                                points = fanCurve.points,
                                defaultPoints = fanCurve.defaultPoints,
                            )
                            preset = preset.copy(fanCurveId = config.catalog.profiles.last().id)
                        }
                        item.joystick?.let { joystick ->
                            val catalog = JoystickProfilePreferences.addImported(
                                prefs = prefs,
                                profile = joystick.value,
                            )
                            preset = preset.copy(joystickId = catalog.profiles.last().id)
                        }
                        item.buttonLayout?.let { buttonLayout ->
                            val catalog = ButtonLayoutProfilePreferences.addImported(
                                prefs,
                                buttonLayout.value,
                            )
                            preset = preset.copy(buttonLayoutId = catalog.profiles.last().id)
                        }
                        item.performance?.let { performance ->
                            val config = PerformanceProfilePreferences.addImported(
                                prefs = prefs,
                                policies = current.performanceProfiles.policies,
                                name = performance.name,
                                maxFrequencies = performance.maxFrequencies,
                            )
                            preset = preset.copy(
                                performanceProfileId = config.profiles.last().id,
                            )
                        }
                        val fanIds = FanCurvePreferences.load(prefs).catalog.profiles
                            .mapTo(mutableSetOf()) { it.id }
                        val joystickIds = JoystickProfilePreferences.load(prefs).profiles
                            .mapTo(mutableSetOf()) { it.id }
                        val performanceIds = PerformanceProfilePreferences.load(
                            prefs,
                            current.performanceProfiles.policies,
                        ).profiles.mapTo(mutableSetOf()) { it.id }
                        val buttonLayoutIds = ButtonLayoutProfilePreferences.load(prefs).profiles
                            .mapTo(mutableSetOf()) { it.id }
                        PresetPreferences.addImported(
                            prefs = prefs,
                            preset = preset,
                            availableFanCurveIds = fanIds,
                            availableJoystickProfileIds = joystickIds,
                            availablePerformanceProfileIds = performanceIds
                                .takeIf { current.performanceProfiles.policies.isNotEmpty() },
                            availableButtonLayoutProfileIds = buttonLayoutIds,
                        )
                    }
                }
            }.isSuccess
            if (success) {
                imported++
                loadPreferences()
            } else {
                failed++
            }
        }
        if (imported > 0) SystemControlService.startOrUpdate(getApplication())
        return ControlImportResult(imported = imported, failed = failed)
    }

    fun addFanCurve(name: String): String {
        val existingIds = mutableState.value.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val template = mutableState.value.fanConfig.activeProfile?.points
            ?: BuiltInFanCurve.NORMAL.factoryPoints
        val config = FanCurvePreferences.add(prefs, name, template)
        mutableState.update { it.copy(fanConfig = config) }
        SystemControlService.startOrUpdate(getApplication())
        return requireNotNull(config.catalog.profiles.firstOrNull { it.id !in existingIds }?.id)
    }

    fun setFanCurve(profileId: String, points: List<FanCurvePoint>) {
        val config = runCatching {
            FanCurvePreferences.savePoints(prefs, profileId, points)
        }.getOrNull() ?: return
        mutableState.update { it.copy(fanConfig = config) }
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setFanCurveAsDefault(profileId: String, points: List<FanCurvePoint>) {
        val config = runCatching {
            FanCurvePreferences.setCurrentAsDefault(prefs, profileId, points)
        }.getOrNull() ?: return
        mutableState.update { it.copy(fanConfig = config) }
        SystemControlService.startOrUpdate(getApplication())
    }

    fun resetFanCurve(profileId: String) {
        val config = FanCurvePreferences.reset(prefs, profileId)
        mutableState.update { it.copy(fanConfig = config) }
        SystemControlService.startOrUpdate(getApplication())
    }

    fun renameFanCurve(profileId: String, name: String) {
        val config = FanCurvePreferences.rename(prefs, profileId, name)
        mutableState.update { it.copy(fanConfig = config) }
        SystemControlService.startOrUpdate(getApplication())
    }

    fun deleteFanCurve(profileId: String) {
        val catalogConfig = FanCurvePreferences.delete(prefs, profileId)
        val curveIds = catalogConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val joystickIds = joystickProfileIds()
        val performanceIds = mutableState.value.performanceProfiles.profiles
            .mapTo(mutableSetOf()) { it.id }
            .takeIf { mutableState.value.performanceProfiles.policies.isNotEmpty() }
        val presets = PresetPreferences.load(
            prefs,
            curveIds,
            joystickIds,
            performanceIds,
        )
        AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presets.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = curveIds,
            availableJoystickProfileIds = joystickIds,
            availablePerformanceProfileIds = performanceIds,
        )
        val config = FanSelectionPreferences.apply(prefs, catalogConfig)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun addPreset(name: String): String {
        val curveIds = mutableState.value.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val (_, id) = PresetPreferences.add(prefs, name, curveIds, joystickProfileIds())
        loadPreferences()
        return id
    }

    fun renamePreset(presetId: String, name: String) {
        val curveIds = mutableState.value.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        PresetPreferences.rename(prefs, presetId, name, curveIds, joystickProfileIds())
        loadPreferences()
    }

    fun deletePreset(presetId: String) {
        val curveIds = mutableState.value.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        PresetPreferences.delete(prefs, presetId, curveIds, joystickProfileIds())
        FanSelectionPreferences.apply(prefs)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setPresetFanCurve(presetId: String, profileId: String?) {
        val curveIds = mutableState.value.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        PresetPreferences.setFanCurve(
            prefs, presetId, profileId, curveIds, joystickProfileIds(),
        )
        FanSelectionPreferences.apply(prefs)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun selectGlobalPreset(presetId: String) {
        val curveIds = mutableState.value.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        PresetPreferences.select(prefs, presetId, curveIds, joystickProfileIds())
        FanSelectionPreferences.apply(prefs)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun selectDefaultProfile(isGame: Boolean, profileId: String) {
        val state = mutableState.value
        PresetPreferences.selectDefault(
            prefs = prefs,
            presetId = profileId,
            isGame = isGame,
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setAppPreset(packageName: String, presetId: String?) {
        val state = mutableState.value
        AppProfilePreferences.setPreset(
            prefs = prefs,
            packageName = packageName,
            presetId = presetId,
            availablePresetIds = state.presetConfig.catalog.presets
                .mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setAppFanCurve(packageName: String, profileId: String?) {
        val state = mutableState.value
        AppProfilePreferences.setFanCurve(
            prefs = prefs,
            packageName = packageName,
            fanCurveId = profileId,
            availablePresetIds = state.presetConfig.catalog.presets
                .mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun addJoystickProfile(name: String): String {
        val (_, id) = JoystickProfilePreferences.add(prefs, name)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
        return id
    }

    fun renameJoystickProfile(profileId: String, name: String) {
        JoystickProfilePreferences.rename(prefs, profileId, name)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setJoystickMode(profileId: String, mode: JoystickRgbMode) {
        JoystickProfilePreferences.setMode(prefs, profileId, mode)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setJoystickColor(profileId: String, red: Int, green: Int, blue: Int) {
        JoystickProfilePreferences.setColor(prefs, profileId, red, green, blue)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setJoystickBrightness(profileId: String, brightness: Int) {
        JoystickProfilePreferences.setBrightness(prefs, profileId, brightness)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun deleteJoystickProfile(profileId: String) {
        val catalog = JoystickProfilePreferences.delete(prefs, profileId)
        val joystickIds = catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val curveIds = mutableState.value.fanConfig.catalog.profiles
            .mapTo(mutableSetOf()) { it.id }
        val presetConfig = PresetPreferences.load(prefs, curveIds, joystickIds)
        AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = curveIds,
            availableJoystickProfileIds = joystickIds,
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setPresetJoystickProfile(presetId: String, profileId: String?) {
        val state = mutableState.value
        PresetPreferences.setJoystickProfile(
            prefs = prefs,
            presetId = presetId,
            joystickProfileId = profileId,
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun addButtonLayoutProfile(name: String): String {
        val (_, id) = ButtonLayoutProfilePreferences.add(prefs, name)
        loadPreferences()
        return id
    }

    fun renameButtonLayoutProfile(profileId: String, name: String) {
        ButtonLayoutProfilePreferences.rename(prefs, profileId, name)
        loadPreferences()
    }

    fun setButtonLayout(profileId: String, layout: FaceButtonLayout) {
        ButtonLayoutProfilePreferences.setLayout(prefs, profileId, layout)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setButtonLayoutM1(profileId: String, mapping: GamepadButtonMapping) {
        ButtonLayoutProfilePreferences.setM1(prefs, profileId, mapping)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setButtonLayoutM2(profileId: String, mapping: GamepadButtonMapping) {
        ButtonLayoutProfilePreferences.setM2(prefs, profileId, mapping)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setButtonLayoutTriggerMode(profileId: String, triggerMode: GamepadTriggerMode) {
        ButtonLayoutProfilePreferences.setTriggerMode(prefs, profileId, triggerMode)
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun deleteButtonLayoutProfile(profileId: String) {
        val catalog = ButtonLayoutProfilePreferences.delete(prefs, profileId)
        val state = mutableState.value
        val buttonLayoutIds = catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val fanIds = state.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val joystickIds = state.joystickProfiles.profiles.mapTo(mutableSetOf()) { it.id }
        val performanceIds = state.performanceProfiles.profiles.mapTo(mutableSetOf()) { it.id }
            .takeIf { state.performanceProfiles.policies.isNotEmpty() }
        val presets = PresetPreferences.load(
            prefs,
            fanIds,
            joystickIds,
            performanceIds,
            buttonLayoutIds,
        )
        AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presets.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanIds,
            availableJoystickProfileIds = joystickIds,
            availablePerformanceProfileIds = performanceIds,
            availableButtonLayoutProfileIds = buttonLayoutIds,
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setPresetButtonLayout(presetId: String, profileId: String?) {
        val state = mutableState.value
        PresetPreferences.setButtonLayout(
            prefs = prefs,
            presetId = presetId,
            buttonLayoutProfileId = profileId,
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
            availablePerformanceProfileIds = state.performanceProfiles.profiles
                .mapTo(mutableSetOf()) { it.id }
                .takeIf { state.performanceProfiles.policies.isNotEmpty() },
            availableButtonLayoutProfileIds = buttonLayoutProfileIds(),
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setAppButtonLayout(packageName: String, profileId: String?) {
        val state = mutableState.value
        AppProfilePreferences.setButtonLayout(
            prefs = prefs,
            packageName = packageName,
            buttonLayoutProfileId = profileId,
            availablePresetIds = state.presetConfig.catalog.presets
                .mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
            availablePerformanceProfileIds = state.performanceProfiles.profiles
                .mapTo(mutableSetOf()) { it.id }
                .takeIf { state.performanceProfiles.policies.isNotEmpty() },
            availableButtonLayoutProfileIds = buttonLayoutProfileIds(),
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun addPerformanceProfile(name: String): String? {
        val policies = mutableState.value.performanceProfiles.policies
        val result = PerformanceProfilePreferences.add(prefs, policies, name) ?: return null
        mutableState.update { it.copy(performanceProfiles = result.first) }
        loadPreferences()
        return result.second
    }

    fun updatePerformanceProfile(
        profileId: String,
        name: String,
        maxFrequencies: Map<Int, Int>,
    ) {
        val policies = mutableState.value.performanceProfiles.policies
        PerformanceProfilePreferences.update(
            prefs = prefs,
            policies = policies,
            profileId = profileId,
            name = name,
            maxFrequencies = maxFrequencies,
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun deletePerformanceProfile(profileId: String) {
        val state = mutableState.value
        val config = PerformanceProfilePreferences.delete(
            prefs,
            state.performanceProfiles.policies,
            profileId,
        )
        val performanceIds = config.profiles.mapTo(mutableSetOf()) { it.id }
        val fanIds = state.fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id }
        val joystickIds = state.joystickProfiles.profiles.mapTo(mutableSetOf()) { it.id }
        val presets = PresetPreferences.load(
            prefs,
            fanIds,
            joystickIds,
            performanceIds,
        )
        AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presets.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanIds,
            availableJoystickProfileIds = joystickIds,
            availablePerformanceProfileIds = performanceIds,
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setPresetPerformanceProfile(presetId: String, profileId: String?) {
        val state = mutableState.value
        PresetPreferences.setPerformanceProfile(
            prefs = prefs,
            presetId = presetId,
            performanceProfileId = profileId,
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
            availablePerformanceProfileIds = state.performanceProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setAppPerformanceProfile(packageName: String, profileId: String?) {
        val state = mutableState.value
        AppProfilePreferences.setPerformanceProfile(
            prefs = prefs,
            packageName = packageName,
            performanceProfileId = profileId,
            availablePresetIds = state.presetConfig.catalog.presets
                .mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
            availablePerformanceProfileIds = state.performanceProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setAppJoystickProfile(packageName: String, profileId: String?) {
        val state = mutableState.value
        AppProfilePreferences.setJoystickProfile(
            prefs = prefs,
            packageName = packageName,
            joystickProfileId = profileId,
            availablePresetIds = state.presetConfig.catalog.presets
                .mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = state.fanConfig.catalog.profiles
                .mapTo(mutableSetOf()) { it.id },
            availableJoystickProfileIds = state.joystickProfiles.profiles
                .mapTo(mutableSetOf()) { it.id },
        )
        loadPreferences()
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(Prefs.OVERLAY_ENABLED, enabled) }
        mutableState.update { it.copy(overlayEnabled = enabled) }
        SystemControlService.startOrUpdate(getApplication())
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(Prefs.AUTO_START_ENABLED, enabled) }
        mutableState.update { it.copy(autoStartEnabled = enabled) }
    }

    fun setProfileSwitchNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(Prefs.PROFILE_SWITCH_NOTIFICATIONS_ENABLED, enabled) }
        mutableState.update { it.copy(profileSwitchNotificationsEnabled = enabled) }
    }

    fun setUsbThermalDisabled(disabled: Boolean) {
        prefs.edit { putBoolean(Prefs.USB_THERMAL_DISABLED, disabled) }
        mutableState.update { it.copy(usbThermalDisabled = disabled) }
        SystemControlService.startOrUpdate(getApplication())
    }

    private fun joystickProfileIds(): Set<String> =
        mutableState.value.joystickProfiles.profiles.mapTo(mutableSetOf()) { it.id }

    private fun buttonLayoutProfileIds(): Set<String> =
        mutableState.value.buttonLayoutProfiles.profiles.mapTo(mutableSetOf()) { it.id }

    private fun refreshPerformancePolicies() {
        viewModelScope.launch(Dispatchers.IO) {
            val policies = CpuFrequencyController.detectPolicies(forceRefresh = true)
            val config = PerformanceProfilePreferences.load(prefs, policies)
            mutableState.update { it.copy(performanceProfiles = config) }
            loadPreferences()
        }
    }
}
