package com.mmax.retrocontrol

import com.mmax.retrocontrol.data.BuiltInFanCurve
import com.mmax.retrocontrol.data.BuiltInPerformanceProfile
import com.mmax.retrocontrol.data.AppControlProfile
import com.mmax.retrocontrol.data.ButtonLayoutProfile
import com.mmax.retrocontrol.data.ButtonLayoutProfileCatalog
import com.mmax.retrocontrol.data.ButtonLayoutProfilePreferences
import com.mmax.retrocontrol.data.ButtonLayoutTilePreferences
import com.mmax.retrocontrol.data.FaceButtonLayout
import com.mmax.retrocontrol.data.GamepadButtonMapping
import com.mmax.retrocontrol.data.GamepadTriggerMode
import com.mmax.retrocontrol.data.FanCurveCatalog
import com.mmax.retrocontrol.data.FanCurvePoint
import com.mmax.retrocontrol.data.FanCurveProfile
import com.mmax.retrocontrol.data.FanCurveSerializer
import com.mmax.retrocontrol.data.ControlPreset
import com.mmax.retrocontrol.data.ControlPresetCatalog
import com.mmax.retrocontrol.data.ControlPresetConfig
import com.mmax.retrocontrol.data.FanSelectionConfig
import com.mmax.retrocontrol.data.FanSelectionPreferences
import com.mmax.retrocontrol.data.FanSelectionSource
import com.mmax.retrocontrol.data.PresetPreferences
import com.mmax.retrocontrol.hardware.FanResponseController
import com.mmax.retrocontrol.hardware.GamepadController
import com.mmax.retrocontrol.hardware.ThermalReading
import com.mmax.retrocontrol.hardware.ThermalSnapshot
import com.mmax.retrocontrol.hardware.ThermalKind
import com.mmax.retrocontrol.hardware.ThermalSensorReader
import com.mmax.retrocontrol.ui.uniqueImportedName
import com.mmax.retrocontrol.ui.retainedPreferencesOnReset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroControlTest {
    @Test
    fun reset_preservesOnlyKernelFanTripBackups() {
        val retained = retainedPreferencesOnReset(
            mapOf(
                "kernel_fan_trip_original_v1_usb_therm_2" to 43_000,
                "usb_thermal_disabled" to true,
                "fan_mode" to "normal",
            )
        )

        assertEquals(
            mapOf("kernel_fan_trip_original_v1_usb_therm_2" to 43_000),
            retained,
        )
    }

    @Test
    fun fanSelection_directCurveOverridesGlobalPreset() {
        val preset = ControlPreset(
            id = ControlPresetCatalog.DEFAULT_ID,
            name = "Default",
            isDefault = true,
            fanCurveId = BuiltInFanCurve.QUIET.id,
        )
        val presets = ControlPresetConfig(
            catalog = ControlPresetCatalog(listOf(preset)),
            selectedPresetId = preset.id,
            selectedNonGamePresetId = preset.id,
        )
        val catalog = FanCurveCatalog()

        assertEquals(
            BuiltInFanCurve.QUIET.id,
            FanSelectionPreferences.resolveTargetProfileId(
                FanSelectionConfig(FanSelectionSource.FollowPreset, enabled = true),
                presets,
                catalog,
            ),
        )
        assertEquals(
            BuiltInFanCurve.PERFORMANCE.id,
            FanSelectionPreferences.resolveTargetProfileId(
                FanSelectionConfig(
                    FanSelectionSource.DirectCurve(BuiltInFanCurve.PERFORMANCE.id),
                    enabled = true,
                ),
                presets,
                catalog,
            ),
        )
        assertNull(
            FanSelectionPreferences.resolveTargetProfileId(
                FanSelectionConfig(FanSelectionSource.FollowPreset, enabled = false),
                presets,
                catalog,
            )
        )
    }

    @Test
    fun foregroundAppCurveResolvesIndependentlyOfTileSelection() {
        val preset = ControlPreset(
            id = ControlPresetCatalog.DEFAULT_ID,
            name = "Default",
            isDefault = true,
            fanCurveId = BuiltInFanCurve.QUIET.id,
        )
        val presets = ControlPresetConfig(
            catalog = ControlPresetCatalog(listOf(preset)),
            selectedPresetId = preset.id,
            selectedNonGamePresetId = preset.id,
        )

        assertEquals(
            BuiltInFanCurve.PERFORMANCE.id,
            FanSelectionPreferences.resolveAppTargetProfileId(
                presetConfig = presets,
                fanCatalog = FanCurveCatalog(),
                appProfile = AppControlProfile(
                    packageName = "example.game",
                    fanCurveId = BuiltInFanCurve.PERFORMANCE.id,
                ),
                appIsGame = true,
            ),
        )
        assertEquals(
            BuiltInFanCurve.QUIET.id,
            FanSelectionPreferences.resolveAppTargetProfileId(
                presetConfig = presets,
                fanCatalog = FanCurveCatalog(),
                appProfile = AppControlProfile(packageName = "example.app"),
            ),
        )
    }

    @Test
    fun foregroundAppWithoutCurveDisablesTileButRemembersPreviousCurve() {
        val selection = FanSelectionPreferences.selectionForAppTarget(
            targetId = null,
            previousSelection = FanSelectionConfig(
                source = FanSelectionSource.DirectCurve(BuiltInFanCurve.PERFORMANCE.id),
                enabled = true,
            ),
            fallbackProfileId = BuiltInFanCurve.QUIET.id,
            fanCatalog = FanCurveCatalog(),
        )

        assertEquals(
            FanSelectionSource.DirectCurve(BuiltInFanCurve.PERFORMANCE.id),
            selection.source,
        )
        assertEquals(false, selection.enabled)
    }

    @Test
    fun presetCatalog_keepsBuiltInDefaultWhenCustomPresetIsRemoved() {
        val default = ControlPresetCatalog.defaultPreset()
        val custom = ControlPreset(id = "custom", name = "Custom")
        val catalog = ControlPresetCatalog(listOf(default, custom)).remove(custom.id)

        assertEquals(listOf(default), catalog.presets)
    }

    @Test
    fun defaultProfiles_areResolvedByAppCategory() {
        val game = ControlPreset(id = "game", name = "Game", fanCurveId = BuiltInFanCurve.PERFORMANCE.id)
        val other = ControlPreset(id = "other", name = "Other", fanCurveId = BuiltInFanCurve.QUIET.id)
        val config = ControlPresetConfig(
            catalog = ControlPresetCatalog(listOf(ControlPresetCatalog.defaultPreset(), game, other)),
            selectedPresetId = game.id,
            selectedNonGamePresetId = other.id,
        )

        assertEquals(game, config.defaultPreset(isGame = true))
        assertEquals(other, config.defaultPreset(isGame = false))
    }

    @Test
    fun removedControl_isNoLongerResolvable() {
        val catalog = FanCurveCatalog().remove(BuiltInFanCurve.NORMAL.id)

        assertNull(catalog.profile(BuiltInFanCurve.NORMAL.id))
    }

    @Test
    fun deletedControls_fallBackToPresetDefaults() {
        val preset = ControlPreset(
            id = "game",
            name = "Game",
            fanCurveId = "deleted-fan",
            joystickId = "deleted-joystick",
            buttonLayoutId = "deleted-buttons",
            performanceProfileId = "deleted-performance",
        )

        val normalized = PresetPreferences.normalizeControlReferences(
            preset = preset,
            availableFanCurveIds = setOf(BuiltInFanCurve.NORMAL.id),
            availableJoystickProfileIds = setOf("available-joystick"),
            availablePerformanceProfileIds = setOf(BuiltInPerformanceProfile.STOCK.id),
            availableButtonLayoutProfileIds = setOf(ButtonLayoutProfileCatalog.NINTENDO_ID),
        )

        assertNull(normalized.fanCurveId)
        assertNull(normalized.joystickId)
        assertNull(normalized.buttonLayoutId)
        assertEquals(BuiltInPerformanceProfile.STOCK.id, normalized.performanceProfileId)
    }

    @Test
    fun legacyProfile_withoutFrequencyProfile_migratesToStock() {
        val normalized = PresetPreferences.normalizeControlReferences(
            preset = ControlPreset(
                id = "legacy",
                name = "Legacy",
                performanceProfileId = null,
            ),
            availableFanCurveIds = setOf(BuiltInFanCurve.NORMAL.id),
            availableJoystickProfileIds = emptySet(),
            availablePerformanceProfileIds = setOf(BuiltInPerformanceProfile.STOCK.id),
            availableButtonLayoutProfileIds = emptySet(),
        )

        assertEquals(BuiltInPerformanceProfile.STOCK.id, normalized.performanceProfileId)
        assertNull(normalized.buttonLayoutId)
    }

    @Test
    fun curveInterpolation_isLinearAndBounded() {
        val points = listOf(FanCurvePoint(40, 25), FanCurvePoint(60, 75))
        assertEquals(0.0, FanCurveSerializer.interpolate(20.0, points), 0.001)
        assertEquals(50.0, FanCurveSerializer.interpolate(50.0, points), 0.001)
        assertEquals(75.0, FanCurveSerializer.interpolate(80.0, points), 0.001)
    }

    @Test
    fun serializer_removesDuplicateTemperatures() {
        val parsed = FanCurveSerializer.parse("40:2,40:7,60:6")
        assertEquals(listOf(40, 60), parsed.map { it.tempC })
        assertEquals(2, parsed.size)
    }

    @Test
    fun catalogCurves_areIndependentAndEditable() {
        val customQuiet = listOf(FanCurvePoint(35, 15), FanCurvePoint(80, 90))
        val catalog = FanCurveCatalog()
        val quiet = requireNotNull(catalog.profile(BuiltInFanCurve.QUIET.id))
        val updated = catalog.replace(quiet.withPoints(customQuiet))

        assertEquals(customQuiet, updated.profile(BuiltInFanCurve.QUIET.id)?.points)
        assertEquals(
            BuiltInFanCurve.NORMAL.factoryPoints,
            updated.profile(BuiltInFanCurve.NORMAL.id)?.points,
        )
        assertEquals(
            BuiltInFanCurve.PERFORMANCE.factoryPoints,
            updated.profile(BuiltInFanCurve.PERFORMANCE.id)?.points,
        )
    }

    @Test
    fun resetBaseline_isOwnedByEachCurve() {
        val original = listOf(FanCurvePoint(40, 20), FanCurvePoint(80, 80))
        val changed = listOf(FanCurvePoint(40, 30), FanCurvePoint(80, 90))
        val profile = FanCurveProfile(
            id = "test",
            customName = "Test",
            points = original,
            defaultPoints = original,
        )

        assertEquals(changed, profile.withCurrentAsDefault(changed).reset().points)
    }

    @Test
    fun transientSpike_isRejectedByThreeSecondMedian() {
        val controller = FanResponseController()
        controller.resetImmediate(tempC = 50.0, percent = 25.0, nowMs = 0L)
        val samples = listOf(50.0, 50.0, 75.0, 50.0, 50.0, 50.0)
        samples.forEachIndexed { index, temp ->
            controller.update(temp, (index + 1) * 500L) { t -> if (t < 60.0) 25.0 else 90.0 }
        }
        assertEquals(25.0, controller.currentLevel(6_000L), 0.001)
    }

    @Test
    fun sustainedChange_rampsOverFiveSeconds() {
        val controller = FanResponseController()
        controller.resetImmediate(tempC = 50.0, percent = 25.0, nowMs = 0L)
        repeat(6) { index ->
            controller.update(60.0, (index + 1) * 500L) { 75.0 }
        }
        assertEquals(25.0, controller.currentLevel(3_000L), 0.001)
        assertEquals(50.0, controller.currentLevel(5_500L), 0.001)
        assertEquals(75.0, controller.currentLevel(8_000L), 0.001)
    }

    @Test
    fun sustainedChange_doesNotRequireAnExactWindowBoundary() {
        val controller = FanResponseController()
        controller.resetImmediate(tempC = 50.0, percent = 25.0, nowMs = 0L)

        listOf(317L, 641L, 966L, 1_292L, 1_619L, 1_947L, 2_276L, 2_606L, 2_936L, 3_267L)
            .forEach { nowMs ->
                controller.update(70.0, nowMs) { 75.0 }
            }

        assertEquals(25.0, controller.currentLevel(3_267L), 0.001)
        assertEquals(50.0, controller.currentLevel(5_767L), 0.001)
        assertEquals(75.0, controller.currentLevel(8_267L), 0.001)
    }

    @Test
    fun outputDeadband_ignoresSmallCurveOutputChange() {
        val controller = FanResponseController()
        controller.resetImmediate(tempC = 50.0, percent = 25.0, nowMs = 0L)
        repeat(6) { index ->
            controller.update(51.9, (index + 1) * 500L) { t -> t * 0.5 }
        }
        assertEquals(25.0, controller.currentLevel(10_000L), 0.001)
    }

    @Test
    fun curveSwitch_canResetImmediately() {
        val controller = FanResponseController()
        controller.resetImmediate(50.0, 25.0, 0L)
        assertEquals(90.0, controller.resetImmediate(50.0, 90.0, 100L), 0.001)
        assertEquals(90.0, controller.currentLevel(100L), 0.001)
    }

    @Test
    fun thermalClassification_keepsOnlyRequestedKinds() {
        assertEquals(ThermalKind.CPU, ThermalSensorReader.classify("cpu-1-3"))
        assertEquals(ThermalKind.CPU, ThermalSensorReader.classify("cpuss-0"))
        assertEquals(ThermalKind.CPU, ThermalSensorReader.classify("mtktscpu"))
        assertEquals(ThermalKind.GPU, ThermalSensorReader.classify("gpuss-7"))
        assertEquals(ThermalKind.GPU, ThermalSensorReader.classify("GPU-thermal"))
        assertEquals(ThermalKind.DDR, ThermalSensorReader.classify("ddr"))
        assertEquals(ThermalKind.DDR, ThermalSensorReader.classify("dram-thermal"))
        assertEquals(ThermalKind.BATTERY, ThermalSensorReader.classify("battery"))
        assertEquals(ThermalKind.BATTERY, ThermalSensorReader.classify("battery-thermal"))
        assertEquals(ThermalKind.USB, ThermalSensorReader.classify("usb-therm"))
        assertNull(ThermalSensorReader.classify("vbat"))
        assertNull(ThermalSensorReader.classify("pm8550_tz"))
    }

    @Test
    fun controlTemperature_usesHotterComponentAverage() {
        val thermal = ThermalSnapshot(
            listOf(
                ThermalReading("thermal_zone1", "cpu-0", ThermalKind.CPU, 40.0),
                ThermalReading("thermal_zone2", "cpu-1", ThermalKind.CPU, 50.0),
                ThermalReading("thermal_zone3", "gpu-0", ThermalKind.GPU, 51.0),
                ThermalReading("thermal_zone4", "gpu-1", ThermalKind.GPU, 55.0),
            )
        )

        assertEquals(45.0, thermal.cpuSummary.averageC, 0.001)
        assertEquals(53.0, thermal.gpuSummary.averageC, 0.001)
        assertEquals(53.0, thermal.controlTempC, 0.001)
    }

    @Test
    fun temperatureNormalization_supportsMilliCelsius() {
        assertEquals(42.5, ThermalSensorReader.normalize(42_500.0), 0.001)
        assertEquals(42.5, ThermalSensorReader.normalize(42.5), 0.001)
        assertTrue(ThermalSensorReader.normalize(-40_960.0) < 0.0)
    }

    @Test
    fun temperatureSummary_reportsHottestSensorName() {
        val thermal = ThermalSnapshot(
            listOf(
                ThermalReading("thermal_zone1", "cpuss-0", ThermalKind.CPU, 42.0),
                ThermalReading("thermal_zone6", "cpu-1-1", ThermalKind.CPU, 47.5),
            )
        )

        assertEquals(44.75, thermal.cpuSummary.averageC, 0.001)
        assertEquals(47.5, thermal.cpuSummary.maxC, 0.001)
        assertEquals("cpu-1-1", thermal.cpuSummary.hottest?.name)
    }

    @Test
    fun buttonLayoutCatalog_startsWithNintendoThenXbox() {
        val catalog = ButtonLayoutProfileCatalog()

        assertEquals(
            listOf(ButtonLayoutProfileCatalog.NINTENDO_ID, ButtonLayoutProfileCatalog.XBOX_ID),
            catalog.profiles.map { it.id },
        )
        assertEquals(
            ButtonLayoutProfileCatalog.NINTENDO_ID,
            ControlPresetCatalog.defaultPreset().buttonLayoutId,
        )
        assertEquals(
            BuiltInPerformanceProfile.STOCK.id,
            ControlPresetCatalog.defaultPreset().performanceProfileId,
        )
        assertEquals(FaceButtonLayout.NINTENDO, ButtonLayoutProfile("custom", "Custom").layout)
    }

    @Test
    fun builtInButtonLayouts_haveFixedNamesAndLayouts() {
        val profiles = ButtonLayoutProfileCatalog.factoryProfiles()

        assertEquals("Nintendo", profiles[0].name)
        assertEquals(FaceButtonLayout.NINTENDO, profiles[0].layout)
        assertEquals("Xbox", profiles[1].name)
        assertEquals(FaceButtonLayout.XBOX, profiles[1].layout)
    }

    @Test
    fun duplicateBackButtonMapping_isAllowed() {
        val profile = ButtonLayoutProfile(
            id = "buttons",
            name = "Buttons",
            m1 = GamepadButtonMapping.START,
            m2 = GamepadButtonMapping.START,
        ).normalized()

        assertEquals(GamepadButtonMapping.START, profile.m1)
        assertEquals(GamepadButtonMapping.START, profile.m2)
    }

    @Test
    fun appButtonLayoutOverride_takesPriorityOverPreset() {
        val presetButtons = ButtonLayoutProfile("preset-buttons", "Preset")
        val appButtons = ButtonLayoutProfile("app-buttons", "App")
        val preset = ControlPreset(
            id = "game",
            name = "Game",
            buttonLayoutId = presetButtons.id,
        )
        val config = ControlPresetConfig(
            catalog = ControlPresetCatalog(listOf(ControlPresetCatalog.defaultPreset(), preset)),
            selectedPresetId = preset.id,
            selectedNonGamePresetId = preset.id,
        )
        val app = AppControlProfile(
            packageName = "example.game",
            buttonLayoutId = appButtons.id,
        )

        assertEquals(
            appButtons.id,
            ButtonLayoutProfilePreferences.resolveTargetProfileId(
                app,
                config,
                ButtonLayoutProfileCatalog(listOf(presetButtons, appButtons)),
                appIsGame = true,
            ),
        )
    }

    @Test
    fun gamepadStateParser_usesDriverValues() {
        assertEquals(
            GamepadController.State(
                FaceButtonLayout.NINTENDO,
                GamepadButtonMapping.L2,
                GamepadButtonMapping.RIGHT,
                GamepadTriggerMode.DIGITAL,
            ),
            GamepadController.parseState(listOf("nintendo", "l2", "right", "digital")),
        )
    }

    @Test
    fun gamepadApplyScript_writesBackButtonsBeforeHotpluggingLayout() {
        val current = GamepadController.State(
            FaceButtonLayout.XBOX,
            GamepadButtonMapping.NONE,
            GamepadButtonMapping.NONE,
            GamepadTriggerMode.BOTH,
        )
        val target = GamepadController.State(
            FaceButtonLayout.NINTENDO,
            GamepadButtonMapping.A,
            GamepadButtonMapping.B,
            GamepadTriggerMode.DIGITAL,
        )
        val lines = GamepadController.buildApplyScript(current, target).lines()

        assertTrue(lines[0].contains("m0_function"))
        assertTrue(lines[1].contains("m1_function"))
        assertTrue(lines[2].contains("/triggers"))
        assertTrue(lines[3].contains("/layout"))
    }

    @Test
    fun buttonLayoutTile_cyclesAllProfilesAndWraps() {
        val catalog = ButtonLayoutProfileCatalog(
            listOf(
                ButtonLayoutProfile("first", "First"),
                ButtonLayoutProfile("second", "Second"),
            ),
        )

        assertEquals(
            "first",
            ButtonLayoutTilePreferences.nextProfile(catalog, null)?.id,
        )
        assertEquals(
            "second",
            ButtonLayoutTilePreferences.nextProfile(
                catalog,
                "first",
            )?.id,
        )
        assertEquals(
            "first",
            ButtonLayoutTilePreferences.nextProfile(catalog, "second")?.id,
        )
    }

    @Test
    fun importedNames_getSmallestAvailableNumericSuffix() {
        val names = mutableSetOf("Profile", "Profile.1")

        assertEquals("Profile.2", uniqueImportedName("Profile", names))
        assertEquals("Other", uniqueImportedName("Other", names))
    }
}
