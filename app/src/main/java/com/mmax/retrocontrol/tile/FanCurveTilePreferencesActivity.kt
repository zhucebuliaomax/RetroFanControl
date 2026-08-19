package com.mmax.retrocontrol.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmax.retrocontrol.MainActivity
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.AmbilightPreferences
import com.mmax.retrocontrol.data.FanCurvePreferences
import com.mmax.retrocontrol.data.ButtonLayoutProfilePreferences
import com.mmax.retrocontrol.data.ButtonLayoutTilePreferences
import com.mmax.retrocontrol.data.FanSelectionPreferences
import com.mmax.retrocontrol.data.FanSelectionSource
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.data.JoystickProfilePreferences
import com.mmax.retrocontrol.data.JoystickSelectionPreferences
import com.mmax.retrocontrol.data.JoystickSelectionSource
import com.mmax.retrocontrol.data.PerformanceProfileConfig
import com.mmax.retrocontrol.data.PerformanceProfilePreferences
import com.mmax.retrocontrol.data.PerformanceTilePreferences
import com.mmax.retrocontrol.data.displayName
import com.mmax.retrocontrol.hardware.CpuFrequencyController
import com.mmax.retrocontrol.hardware.BatteryConnectionReader
import com.mmax.retrocontrol.service.SystemControlService
import com.mmax.retrocontrol.theme.RetroControlTheme
import kotlin.math.roundToInt

/** Routes Quick Settings long presses and renders the matching chooser as a dialog window. */
class FanCurveTilePreferencesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        if (originatingTile()?.className == OverlayTileService::class.java.name) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
            return
        }

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window.setDimAmount(0.32f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.CENTER)
        setFinishOnTouchOutside(true)

        if (originatingTile()?.className == ChargingQuickSettingsTile::class.java.name) {
            showChargingThresholdDialog()
            return
        }

        if (originatingTile()?.className == AmbilightQuickSettingsTile::class.java.name) {
            showAmbilightBrightnessDialog()
            return
        }

        val joystickTile = originatingTile()?.className ==
            JoystickQuickSettingsTile::class.java.name
        val performanceTile = originatingTile()?.className ==
            PerformanceQuickSettingsTile::class.java.name
        val buttonLayoutTile = originatingTile()?.className ==
            ButtonLayoutQuickSettingsTile::class.java.name
        setContent {
            RetroControlTheme {
                val config by remember { mutableStateOf(currentConfig()) }
                val selection by remember { mutableStateOf(currentSelection(config)) }
                val joystickCatalog by remember {
                    mutableStateOf(
                        JoystickProfilePreferences.load(
                            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
                        )
                    )
                }
                val joystickSelection by remember {
                    mutableStateOf(
                        JoystickSelectionPreferences.load(
                            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                            joystickCatalog,
                        )
                    )
                }
                val performanceConfig by remember {
                    mutableStateOf(
                        if (performanceTile) {
                            PerformanceProfilePreferences.load(
                                getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                                CpuFrequencyController.detectPolicies(),
                            )
                        } else {
                            PerformanceProfileConfig()
                        }
                    )
                }
                val buttonLayoutCatalog by remember {
                    mutableStateOf(
                        ButtonLayoutProfilePreferences.load(
                            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                        ),
                    )
                }
                val selectedButtonLayoutId = remember(buttonLayoutCatalog) {
                    ButtonLayoutTilePreferences.selectedProfileId(
                        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                        buttonLayoutCatalog,
                    )
                }
                val selectedPerformanceProfileId = remember(performanceConfig) {
                    currentPerformanceProfileId(performanceConfig)
                }
                Surface(
                    modifier = Modifier.width(360.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        Modifier.padding(
                            start = 24.dp,
                            top = 24.dp,
                            end = 24.dp,
                            bottom = 12.dp,
                        )
                    ) {
                        Text(
                            text = stringResource(
                                when {
                                    performanceTile -> R.string.select_performance_profile
                                    buttonLayoutTile -> R.string.select_button_layout
                                    joystickTile -> R.string.select_joystick_profile
                                    else -> R.string.select_fan_curve
                                }
                            ),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Spacer(Modifier.height(ListItemDefaults.SegmentedGap * 4))
                        val sources = buildList {
                            if (performanceTile) {
                                performanceConfig.profiles.forEach { profile ->
                                    add(
                                        TileSourceUi(
                                            name = profile.displayName(
                                                this@FanCurveTilePreferencesActivity
                                            ),
                                            selected = selectedPerformanceProfileId == profile.id,
                                            onClick = { selectPerformanceProfile(profile.id) },
                                        )
                                    )
                                }
                            } else if (buttonLayoutTile) {
                                buttonLayoutCatalog.profiles.forEach { profile ->
                                    add(
                                        TileSourceUi(
                                            name = profile.name,
                                            selected = selectedButtonLayoutId == profile.id,
                                            onClick = { selectButtonLayout(profile.id) },
                                        ),
                                    )
                                }
                            } else if (joystickTile) {
                                add(
                                    TileSourceUi(
                                        name = getString(R.string.follow_profile),
                                        selected = joystickSelection.source is
                                            JoystickSelectionSource.FollowProfile,
                                        onClick = ::selectFollowJoystickProfile,
                                    )
                                )
                                joystickCatalog.profiles.forEach { profile ->
                                    add(
                                        TileSourceUi(
                                            name = profile.name,
                                            selected = (
                                                joystickSelection.source as?
                                                    JoystickSelectionSource.DirectProfile
                                                )?.profileId == profile.id,
                                            onClick = { selectJoystick(profile.id) },
                                        )
                                    )
                                }
                            } else {
                                add(
                                    TileSourceUi(
                                        name = getString(R.string.follow_profile),
                                        selected = selection.source is FanSelectionSource.FollowPreset,
                                        onClick = ::selectFollowPreset,
                                    )
                                )
                                config.catalog.profiles.forEach { profile ->
                                    add(
                                        TileSourceUi(
                                            name = profile.displayName(
                                                this@FanCurveTilePreferencesActivity
                                            ),
                                            selected = (
                                                selection.source as? FanSelectionSource.DirectCurve
                                                )?.profileId == profile.id,
                                            onClick = { select(profile.id) },
                                        )
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            sources.forEachIndexed { index, source ->
                                FanSourceRow(
                                    name = source.name,
                                    selected = source.selected,
                                    onClick = source.onClick,
                                    index = index,
                                    count = sources.size,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { finish() },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun showAmbilightBrightnessDialog() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        setContent {
            RetroControlTheme {
                var node by remember {
                    mutableIntStateOf(
                        (AmbilightPreferences.brightness(prefs) * 10 / 255f).roundToInt()
                    )
                }
                Surface(
                    modifier = Modifier.width(360.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.ambilight_brightness),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${node * 10}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = node.toFloat(),
                            onValueChange = { node = it.roundToInt().coerceIn(0, 10) },
                            onValueChangeFinished = {
                                AmbilightPreferences.setBrightness(
                                    prefs,
                                    (node * 255 / 10f).roundToInt(),
                                )
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { finish() },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun showChargingThresholdDialog() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        setContent {
            RetroControlTheme {
                var threshold by remember {
                    mutableIntStateOf(ChargingControlPreferences.load(prefs).threshold)
                }
                Surface(
                    modifier = Modifier.width(360.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.charging_threshold_title),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "$threshold%",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = threshold.toFloat(),
                            onValueChange = { value ->
                                threshold = ChargingControlPreferences.normalizeThreshold(
                                    value.roundToInt()
                                )
                            },
                            onValueChangeFinished = {
                                ChargingControlPreferences.setThresholdAndSelect(prefs, threshold)
                                ChargingQuickSettingsTile.requestRefresh(this@FanCurveTilePreferencesActivity)
                                if (BatteryConnectionReader.read(this@FanCurveTilePreferencesActivity).powerConnected) {
                                    RootAccessManager.ensureRoot { granted ->
                                        if (granted) {
                                            SystemControlService.updateChargingControl(
                                                applicationContext
                                            )
                                        }
                                    }
                                }
                            },
                            valueRange = ChargingControlPreferences.MIN_THRESHOLD.toFloat()..
                                ChargingControlPreferences.MAX_THRESHOLD.toFloat(),
                            steps = 9,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { finish() },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
            }
        }
    }

    private fun originatingTile(): ComponentName? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
        }

    private fun currentConfig() =
        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).let { prefs ->
            FanSelectionPreferences.apply(prefs, FanCurvePreferences.load(prefs))
        }

    private fun currentSelection(config: com.mmax.retrocontrol.data.FanControlConfig) =
        FanSelectionPreferences.load(
            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
            config,
        )

    private fun currentPerformanceProfileId(config: PerformanceProfileConfig): String? {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        return PerformanceTilePreferences.selectedProfileId(prefs, config)
            ?: prefs.getString(Prefs.LAST_APPLIED_PERFORMANCE_PROFILE, null)
                ?.takeIf { config.profile(it) != null }
            ?: config.stockProfile?.id
    }

    private fun select(profileId: String) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        FanSelectionPreferences.selectDirectCurve(prefs, profileId)
        CurrentAppControls.setFan(this, prefs, profileId)
        finishSelection()
    }

    private fun selectFollowPreset() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val config = FanSelectionPreferences.selectFollowPreset(prefs)
        CurrentAppControls.setFan(this, prefs, config.activeProfileId)
        finishSelection()
    }

    private fun selectJoystick(profileId: String) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        JoystickSelectionPreferences.selectDirectProfile(prefs, profileId)
        CurrentAppControls.setJoystick(this, prefs, profileId)
        finishJoystickSelection()
    }

    private fun selectFollowJoystickProfile() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        JoystickSelectionPreferences.selectFollowProfile(prefs)
        finishJoystickSelection()
    }

    private fun selectPerformanceProfile(profileId: String) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        PerformanceTilePreferences.select(prefs, profileId)
        CurrentAppControls.setPerformance(this, prefs, profileId)
        PerformanceQuickSettingsTile.requestRefresh(this)
        RootAccessManager.ensureRoot {
            SystemControlService.startOrUpdate(applicationContext)
            finish()
        }
    }

    private fun selectButtonLayout(profileId: String) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        ButtonLayoutTilePreferences.select(prefs, profileId)
        CurrentAppControls.setButtonLayout(this, prefs, profileId)
        ButtonLayoutQuickSettingsTile.requestRefresh(this)
        RootAccessManager.ensureRoot {
            SystemControlService.startOrUpdate(applicationContext)
            finish()
        }
    }

    private fun finishJoystickSelection() {
        JoystickQuickSettingsTile.requestRefresh(this)
        RootAccessManager.ensureRoot {
            SystemControlService.startOrUpdate(applicationContext)
            finish()
        }
    }

    private fun finishSelection() {
        FanQuickSettingsTile.requestRefresh(this)
        RootAccessManager.ensureRoot {
            SystemControlService.startOrUpdate(applicationContext)
            finish()
        }
    }
}

private data class TileSourceUi(
    val name: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@androidx.compose.runtime.Composable
private fun FanSourceRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        content = { Text(text = name, maxLines = 1) },
        modifier = Modifier.fillMaxWidth(),
    )
}
