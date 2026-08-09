@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.BuildConfig
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.ControlItemJson
import com.mmax.retrocontrol.data.FanCurvePoint
import com.mmax.retrocontrol.data.AppProfilePreferences
import com.mmax.retrocontrol.data.displayName
import com.mmax.retrocontrol.designsystem.FocusScrollMargin
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus
import com.mmax.retrocontrol.feature.authorization.AuthorizationManagementSection
import com.mmax.retrocontrol.feature.authorization.AuthorizationUiState
import com.mmax.retrocontrol.feature.fan.FanProfileItemUiState
import com.mmax.retrocontrol.feature.fan.FanProfileSectionState
import com.mmax.retrocontrol.feature.fan.FanProfilesSection
import com.mmax.retrocontrol.feature.joystick.JoystickProfileEditorDialog
import com.mmax.retrocontrol.feature.joystick.JoystickProfileUiState
import com.mmax.retrocontrol.feature.joystick.JoystickProfilesSection
import com.mmax.retrocontrol.feature.joystick.JoystickRgbMode
import com.mmax.retrocontrol.feature.joystick.R as JoystickR
import com.mmax.retrocontrol.service.SystemControlService
import com.mmax.retrocontrol.service.MediaProjectionActivity
import com.mmax.retrocontrol.tile.OverlayPermissionActivity
import com.mmax.retrocontrol.util.formatFanPercent
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class ExportListKind { PRESET, FAN, JOYSTICK, BUTTON_LAYOUT, PERFORMANCE }

private data class PendingExportFile(
    val itemName: String,
    val json: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel = viewModel(),
    startOnAccess: Boolean = false,
    onProfileSwitchToastsEnabled: (Boolean) -> Unit = {},
    onRefreshRoot: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hasRoot by RootAccessManager.hasRoot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var exportListKind by remember { mutableStateOf<ExportListKind?>(null) }
    var pendingExportFiles by remember { mutableStateOf<List<PendingExportFile>>(emptyList()) }
    val importItemsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = result.data.selectedDocumentUris()
            var readFailures = 0
            val jsonFiles = uris.mapNotNull { uri ->
                runCatching {
                    val displayName = context.contentResolver.displayName(uri)
                    require(displayName == null || displayName.endsWith(".json", ignoreCase = true))
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Unable to open selected file")
                }.getOrElse {
                    readFailures++
                    null
                }
            }
            val imported = vm.importControlItems(jsonFiles)
            Toast.makeText(
                context,
                resources.getString(
                    R.string.items_import_result,
                    imported.imported,
                    imported.failed + readFailures,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val exportItemsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val treeUri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
        if (treeUri != null) {
            val (exported, failed) = context.exportControlFiles(treeUri, pendingExportFiles)
            Toast.makeText(
                context,
                resources.getString(R.string.items_export_result, exported, failed),
                Toast.LENGTH_SHORT,
            ).show()
            pendingExportFiles = emptyList()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var restoreFanCurveFocusId by remember { mutableStateOf<String?>(null) }
    var editingPresetId by remember { mutableStateOf<String?>(null) }
    var restorePresetFocusId by remember { mutableStateOf<String?>(null) }
    var editingJoystickProfileId by remember { mutableStateOf<String?>(null) }
    var restoreJoystickFocusId by remember { mutableStateOf<String?>(null) }
    var editingButtonLayoutProfileId by remember { mutableStateOf<String?>(null) }
    var restoreButtonLayoutFocusId by remember { mutableStateOf<String?>(null) }
    var editingPerformanceProfileId by remember { mutableStateOf<String?>(null) }
    var restorePerformanceFocusId by remember { mutableStateOf<String?>(null) }
    var selectedDestination by rememberSaveable {
        mutableStateOf(
            if (startOnAccess) DashboardDestination.ACCESS
            else DashboardDestination.CONTROLS
        )
    }
    var selectedControl by rememberSaveable { mutableStateOf<ControlModule?>(null) }
    var selectedApp by rememberSaveable { mutableStateOf<String?>(null) }
    var lastBackPressedAt by remember { mutableLongStateOf(0L) }
    var notificationsEnabled by remember {
        mutableStateOf(context.areFanNotificationsEnabled())
    }
    var overlayPermissionGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    val fanProfileIds = state.fanConfig.catalog.profiles.map { it.id }
    val fanProfileFocusRequesters = remember(fanProfileIds) {
        List(fanProfileIds.size + 1) { FocusRequester() }
    }
    val addCurveFocusRequester = remember { FocusRequester() }
    val joystickProfileIds = state.joystickProfiles.profiles.map { it.id }
    val joystickProfileFocusRequesters = remember(joystickProfileIds) {
        List(joystickProfileIds.size + 1) { FocusRequester() }
    }
    val addJoystickProfileFocusRequester = remember { FocusRequester() }
    val buttonLayoutProfileIds = state.buttonLayoutProfiles.profiles.map { it.id }
    val buttonLayoutProfileFocusRequesters = remember(buttonLayoutProfileIds) {
        List(buttonLayoutProfileIds.size + 1) { FocusRequester() }
    }
    val addButtonLayoutProfileFocusRequester = remember { FocusRequester() }
    val performanceProfileIds = state.performanceProfiles.profiles.map { it.id }
    val performanceProfileFocusRequesters = remember(performanceProfileIds) {
        List(performanceProfileIds.size) { FocusRequester() }
    }
    val addPerformanceProfileFocusRequester = remember { FocusRequester() }
    val presetIds = state.presetConfig.catalog.presets.map { it.id }
    val presetFocusRequesters = remember(presetIds) {
        List(presetIds.size) { FocusRequester() }
    }
    val addPresetFocusRequester = remember { FocusRequester() }
    val appFocusRequester = remember { FocusRequester() }
    val authorizationFocusRequesters = remember { List(10) { FocusRequester() } }
    val githubFocusRequester = remember { FocusRequester() }
    val navigationFocusRequesters = remember {
        List(DashboardDestination.entries.size) { FocusRequester() }
    }
    val controlFocusRequesters = remember {
        List(ControlModule.entries.size) { FocusRequester() }
    }
    val emptyDetailFocusRequester = remember { FocusRequester() }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = context.areFanNotificationsEnabled()
                overlayPermissionGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val thermal = state.telemetry.thermal
    val pressBackAgainToExit = stringResource(R.string.press_back_again_to_exit)
    val offName = stringResource(R.string.fan_mode_off)
    val joystickOffName = stringResource(JoystickR.string.joystick_off)
    fun fanCurveName(profileId: String?): String = profileId
        ?.let { state.fanConfig.catalog.profile(it)?.displayName(context) }
        ?: offName
    fun joystickProfileName(profileId: String?): String = profileId
        ?.let { state.joystickProfiles.profile(it)?.name }
        ?: joystickOffName
    val unmanagedButtonLayoutName = stringResource(R.string.button_layout_unmanaged)
    fun buttonLayoutProfileName(profileId: String?): String = profileId
        ?.let { state.buttonLayoutProfiles.profile(it)?.name }
        ?: unmanagedButtonLayoutName
    val stockPerformanceName = stringResource(R.string.performance_stock)
    fun performanceProfileName(profileId: String?): String = profileId
        ?.let { state.performanceProfiles.profile(it)?.displayName(context) }
        ?: stockPerformanceName
    fun joystickProfileUi(profileId: String): JoystickProfileUiState? =
        state.joystickProfiles.profile(profileId)?.let { profile ->
            JoystickProfileUiState(
                id = profile.id,
                name = profile.name,
                mode = profile.mode,
                red = profile.red,
                green = profile.green,
                blue = profile.blue,
                brightness = profile.brightness,
            )
        }
    val presetItems = state.presetConfig.catalog.presets.map { preset ->
        PresetListItemUiState(
            id = preset.id,
            name = preset.name,
            isDefault = preset.isDefault,
            fanCurveName = fanCurveName(preset.fanCurveId),
            joystickProfileName = joystickProfileName(preset.joystickId),
            buttonLayoutName = buttonLayoutProfileName(preset.buttonLayoutId),
            performanceProfileName = performanceProfileName(preset.performanceProfileId),
        )
    }
    val appsWithProfileSummaries = state.installedApps.map { app ->
        val profile = state.appProfiles[app.packageName]
        val effectivePreset = AppProfilePreferences.effectivePreset(
            profile = profile,
            presetConfig = state.presetConfig,
            isGame = app.isGame,
        )
        val summary = buildList {
            add(effectivePreset.name)
            profile?.fanCurveId?.let { add(fanCurveName(it)) }
            profile?.joystickId?.let { add(joystickProfileName(it)) }
            profile?.buttonLayoutId?.let { add(buttonLayoutProfileName(it)) }
            profile?.performanceProfileId?.let { add(performanceProfileName(it)) }
        }.joinToString(" · ")
        app.copy(profileSummary = summary)
    }

    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressedAt <= 2_000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressedAt = now
            Toast.makeText(
                context,
                pressBackAgainToExit,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(
        editingProfileId,
        restoreFanCurveFocusId,
        fanProfileIds,
    ) {
        val restoreId = restoreFanCurveFocusId
        if (editingProfileId == null && restoreId != null) {
            withFrameNanos { }
            val selectedIndex = fanProfileIds
                .indexOf(restoreId)
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 0
            fanProfileFocusRequesters[selectedIndex].requestFocus()
            restoreFanCurveFocusId = null
        }
    }

    LaunchedEffect(
        editingPerformanceProfileId,
        restorePerformanceFocusId,
        performanceProfileIds,
    ) {
        val restoreId = restorePerformanceFocusId
        if (editingPerformanceProfileId == null && restoreId != null) {
            withFrameNanos { }
            performanceProfileIds.indexOf(restoreId)
                .takeIf { it >= 0 }
                ?.let { performanceProfileFocusRequesters[it].requestFocus() }
            restorePerformanceFocusId = null
        }
    }

    LaunchedEffect(editingPresetId, restorePresetFocusId, presetIds) {
        val restoreId = restorePresetFocusId
        if (editingPresetId == null && restoreId != null) {
            withFrameNanos { }
            val index = presetIds.indexOf(restoreId).takeIf { it >= 0 } ?: 0
            presetFocusRequesters[index].requestFocus()
            restorePresetFocusId = null
        }
    }

    LaunchedEffect(
        editingJoystickProfileId,
        restoreJoystickFocusId,
        joystickProfileIds,
    ) {
        val restoreId = restoreJoystickFocusId
        if (editingJoystickProfileId == null && restoreId != null) {
            withFrameNanos { }
            val index = joystickProfileIds.indexOf(restoreId)
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 0
            joystickProfileFocusRequesters[index].requestFocus()
            restoreJoystickFocusId = null
        }
    }

    LaunchedEffect(
        editingButtonLayoutProfileId,
        restoreButtonLayoutFocusId,
        buttonLayoutProfileIds,
    ) {
        val restoreId = restoreButtonLayoutFocusId
        if (editingButtonLayoutProfileId == null && restoreId != null) {
            withFrameNanos { }
            buttonLayoutProfileIds.indexOf(restoreId)
                .takeIf { it >= 0 }
                ?.let { buttonLayoutProfileFocusRequesters[it + 1].requestFocus() }
            restoreButtonLayoutFocusId = null
        }
    }

    DisposableEffect(editingJoystickProfileId, context) {
        val profileId = editingJoystickProfileId
        if (profileId != null) {
            SystemControlService.previewJoystickProfile(context, profileId)
        }
        onDispose {
            if (profileId != null) {
                SystemControlService.stopJoystickProfilePreview(context)
            }
        }
    }

    DisposableEffect(editingProfileId, context) {
        val profileId = editingProfileId
        if (profileId != null) {
            SystemControlService.previewFanProfile(context, profileId)
        }
        onDispose {
            if (profileId != null) {
                SystemControlService.stopFanProfilePreview(context)
            }
        }
    }

    AdaptiveDashboardScaffold(
        selectedDestination = selectedDestination,
        onDestinationSelected = {
            selectedDestination = it
            selectedControl = null
            selectedApp = null
        },
        selectedControl = selectedControl,
        onControlSelected = { selectedControl = it },
        selectedApp = selectedApp,
        installedApps = appsWithProfileSummaries,
        onAppSelected = { selectedApp = it },
        navigationFocusRequesters = navigationFocusRequesters,
        controlFocusRequesters = controlFocusRequesters,
        appFocusRequester = appFocusRequester,
        emptyDetailFocusRequester = emptyDetailFocusRequester,
        fanContent = {
            FanProfilesSection(
                state = FanProfileSectionState(
                    profiles = state.fanConfig.catalog.profiles.map { profile ->
                        FanProfileItemUiState(
                            id = profile.id,
                            name = profile.displayName(context),
                            controlPointCount = profile.points.size,
                        )
                    },
                ),
                onProfileSelected = { profileId ->
                    editingProfileId = profileId
                },
                onDeleteProfile = vm::deleteFanCurve,
                showTitle = false,
                offModifier = Modifier
                    .focusRequester(fanProfileFocusRequesters[0])
                    .focusProperties {
                        up = FocusRequester.Default
                        down = if (fanProfileIds.isEmpty()) {
                            addCurveFocusRequester
                        } else {
                            fanProfileFocusRequesters[1]
                        }
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                profileModifier = { index ->
                    val focusIndex = index + 1
                    Modifier
                        .focusRequester(fanProfileFocusRequesters[focusIndex])
                        .focusProperties {
                            up = fanProfileFocusRequesters[focusIndex - 1]
                            down = if (index == fanProfileIds.lastIndex) {
                                addCurveFocusRequester
                            } else {
                                fanProfileFocusRequesters[focusIndex + 1]
                            }
                            left = FocusRequester.Default
                            right = FocusRequester.Default
                        }
                },
            )
        },
        fanAction = {
            ControlTransferFabMenu(
                addLabel = stringResource(R.string.add_fan_curve),
                onAdd = {
                    editingProfileId = vm.addFanCurve(
                        resources.getString(R.string.new_fan_curve)
                    )
                },
                onImport = { importItemsLauncher.launch(controlItemsImportIntent()) },
                onExport = { exportListKind = ExportListKind.FAN },
                modifier = Modifier
                    .focusRequester(addCurveFocusRequester)
                    .focusProperties {
                        up = fanProfileFocusRequesters.last()
                        down = FocusRequester.Default
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
        joystickContent = {
            JoystickProfilesSection(
                profiles = state.joystickProfiles.profiles.map { profile ->
                    JoystickProfileUiState(
                        id = profile.id,
                        name = profile.name,
                        mode = profile.mode,
                        red = profile.red,
                        green = profile.green,
                        blue = profile.blue,
                        brightness = profile.brightness,
                    )
                },
                onProfileSelected = { editingJoystickProfileId = it },
                onDeleteProfile = vm::deleteJoystickProfile,
                offModifier = Modifier
                    .focusRequester(joystickProfileFocusRequesters[0])
                    .focusProperties {
                        up = FocusRequester.Default
                        down = if (joystickProfileIds.isEmpty()) {
                            addJoystickProfileFocusRequester
                        } else {
                            joystickProfileFocusRequesters[1]
                        }
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                profileModifier = { index ->
                    val focusIndex = index + 1
                    Modifier
                        .focusRequester(joystickProfileFocusRequesters[focusIndex])
                        .focusProperties {
                            up = joystickProfileFocusRequesters[focusIndex - 1]
                            down = if (index == joystickProfileIds.lastIndex) {
                                addJoystickProfileFocusRequester
                            } else {
                                joystickProfileFocusRequesters[focusIndex + 1]
                            }
                            left = FocusRequester.Default
                            right = FocusRequester.Default
                        }
                },
            )
        },
        joystickAction = {
            ControlTransferFabMenu(
                addLabel = stringResource(JoystickR.string.joystick_add_profile),
                onAdd = {
                    editingJoystickProfileId = vm.addJoystickProfile(
                        resources.getString(JoystickR.string.joystick_new_profile)
                    )
                },
                onImport = { importItemsLauncher.launch(controlItemsImportIntent()) },
                onExport = { exportListKind = ExportListKind.JOYSTICK },
                modifier = Modifier
                    .focusRequester(addJoystickProfileFocusRequester)
                    .focusProperties {
                        up = joystickProfileFocusRequesters.last()
                        down = FocusRequester.Default
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
        buttonLayoutContent = {
            ButtonLayoutProfilesSection(
                profiles = state.buttonLayoutProfiles.profiles,
                followSystemName = unmanagedButtonLayoutName,
                onProfileSelected = { editingButtonLayoutProfileId = it },
                onDeleteProfile = vm::deleteButtonLayoutProfile,
                followSystemModifier = Modifier
                    .focusRequester(buttonLayoutProfileFocusRequesters[0])
                    .focusProperties {
                        up = FocusRequester.Default
                        down = buttonLayoutProfileFocusRequesters.getOrNull(1)
                            ?: addButtonLayoutProfileFocusRequester
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                profileModifier = { index ->
                    Modifier
                        .focusRequester(buttonLayoutProfileFocusRequesters[index + 1])
                        .focusProperties {
                            up = buttonLayoutProfileFocusRequesters[index]
                            down = if (index == buttonLayoutProfileIds.lastIndex) {
                                addButtonLayoutProfileFocusRequester
                            } else {
                                buttonLayoutProfileFocusRequesters[index + 2]
                            }
                            left = FocusRequester.Default
                            right = FocusRequester.Default
                        }
                },
            )
        },
        buttonLayoutAction = {
            ControlTransferFabMenu(
                addLabel = stringResource(R.string.add_button_layout),
                onAdd = {
                    editingButtonLayoutProfileId = vm.addButtonLayoutProfile(
                        resources.getString(R.string.new_button_layout),
                    )
                },
                onImport = { importItemsLauncher.launch(controlItemsImportIntent()) },
                onExport = { exportListKind = ExportListKind.BUTTON_LAYOUT },
                modifier = Modifier
                    .focusRequester(addButtonLayoutProfileFocusRequester)
                    .focusProperties {
                        up = buttonLayoutProfileFocusRequesters.lastOrNull()
                            ?: FocusRequester.Default
                        down = FocusRequester.Default
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
        presetContent = {
            val gameProfile = state.presetConfig.defaultPreset(true)
            val nonGameProfile = state.presetConfig.defaultPreset(false)
            DefaultProfileSection(
                gameProfileId = gameProfile.id,
                gameProfileName = gameProfile.name,
                nonGameProfileId = nonGameProfile.id,
                nonGameProfileName = nonGameProfile.name,
                profileChoices = state.presetConfig.catalog.presets.map {
                    AppProfileChoice(it.id, it.name)
                },
                onDefaultProfileSelected = { isGame, id ->
                    vm.selectDefaultProfile(isGame, id)
                },
                onProfileEdit = { editingPresetId = it },
                onAddProfile = {
                    editingPresetId = vm.addPreset(resources.getString(R.string.new_preset))
                },
            )
            Spacer(Modifier.height(24.dp))
            PresetManagementSection(
                presets = presetItems,
                onPresetClick = { editingPresetId = it },
                onDeletePreset = vm::deletePreset,
                itemModifier = { index ->
                    Modifier
                        .focusRequester(presetFocusRequesters[index])
                        .focusProperties {
                            up = if (index == 0) {
                                FocusRequester.Default
                            } else {
                                presetFocusRequesters[index - 1]
                            }
                            down = if (index == presetItems.lastIndex) {
                                addPresetFocusRequester
                            } else {
                                presetFocusRequesters[index + 1]
                            }
                            left = FocusRequester.Default
                            right = FocusRequester.Default
                        }
                },
            )
        },
        presetAction = {
            ControlTransferFabMenu(
                addLabel = stringResource(R.string.add_preset),
                onAdd = {
                    editingPresetId = vm.addPreset(resources.getString(R.string.new_preset))
                },
                onImport = { importItemsLauncher.launch(controlItemsImportIntent()) },
                onExport = { exportListKind = ExportListKind.PRESET },
                modifier = Modifier
                    .focusRequester(addPresetFocusRequester)
                    .focusProperties {
                        up = presetFocusRequesters.last()
                        down = FocusRequester.Default
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
        performanceContent = {
            PerformanceProfilesSection(
                config = state.performanceProfiles,
                onProfileSelected = { editingPerformanceProfileId = it },
                onDeleteProfile = vm::deletePerformanceProfile,
                profileModifier = { index ->
                    Modifier
                        .focusRequester(performanceProfileFocusRequesters[index])
                        .focusProperties {
                            up = if (index == 0) {
                                FocusRequester.Default
                            } else {
                                performanceProfileFocusRequesters[index - 1]
                            }
                            down = if (index == performanceProfileIds.lastIndex) {
                                addPerformanceProfileFocusRequester
                            } else {
                                performanceProfileFocusRequesters[index + 1]
                            }
                            left = FocusRequester.Default
                            right = FocusRequester.Default
                        }
                },
            )
        },
        performanceAction = {
            ControlTransferFabMenu(
                addLabel = stringResource(R.string.add_performance_profile),
                onAdd = {
                    vm.addPerformanceProfile(
                        resources.getString(R.string.new_performance_profile)
                    )?.let { editingPerformanceProfileId = it }
                },
                onImport = { importItemsLauncher.launch(controlItemsImportIntent()) },
                onExport = { exportListKind = ExportListKind.PERFORMANCE },
                modifier = Modifier
                    .focusRequester(addPerformanceProfileFocusRequester)
                    .focusProperties {
                        up = performanceProfileFocusRequesters.lastOrNull()
                            ?: FocusRequester.Default
                        down = FocusRequester.Default
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
        appProfileContent = { packageName ->
            val profile = state.appProfiles[packageName]
            val app = state.installedApps.firstOrNull { it.packageName == packageName }
            val effectivePreset = AppProfilePreferences.effectivePreset(
                profile = profile,
                presetConfig = state.presetConfig,
                isGame = app?.isGame == true,
            )
            AppProfileSection(
                profile = profile,
                selectedProfileName = if (profile?.presetId == null) {
                    resources.getString(R.string.follow_default)
                } else effectivePreset.name,
                selectedFanCurveName = profile?.fanCurveId?.let(::fanCurveName)
                    ?: resources.getString(R.string.follow_profile),
                selectedJoystickProfileName = profile?.joystickId
                    ?.let(::joystickProfileName)
                    ?: resources.getString(R.string.follow_profile),
                selectedButtonLayoutName = profile?.buttonLayoutId
                    ?.let(::buttonLayoutProfileName)
                    ?: resources.getString(R.string.follow_profile),
                selectedPerformanceProfileName = profile?.performanceProfileId
                    ?.let(::performanceProfileName)
                    ?: resources.getString(R.string.follow_profile),
                profileChoices = buildList {
                    add(AppProfileChoice(null, resources.getString(R.string.follow_default)))
                    state.presetConfig.catalog.presets.forEach {
                        add(AppProfileChoice(it.id, it.name))
                    }
                },
                fanCurveChoices = buildList {
                    add(AppProfileChoice(null, resources.getString(R.string.follow_profile)))
                    state.fanConfig.catalog.profiles.forEach { fanProfile ->
                        add(AppProfileChoice(fanProfile.id, fanProfile.displayName(context)))
                    }
                },
                joystickChoices = buildList {
                    add(AppProfileChoice(null, resources.getString(R.string.follow_profile)))
                    state.joystickProfiles.profiles.forEach { joystickProfile ->
                        add(AppProfileChoice(joystickProfile.id, joystickProfile.name))
                    }
                },
                buttonLayoutChoices = buildList {
                    add(AppProfileChoice(null, resources.getString(R.string.follow_profile)))
                    state.buttonLayoutProfiles.profiles.forEach { buttonLayout ->
                        add(AppProfileChoice(buttonLayout.id, buttonLayout.name))
                    }
                },
                performanceChoices = buildList {
                    add(AppProfileChoice(null, resources.getString(R.string.follow_profile)))
                    state.performanceProfiles.profiles.forEach { performanceProfile ->
                        add(
                            AppProfileChoice(
                                performanceProfile.id,
                                performanceProfile.displayName(context),
                            )
                        )
                    }
                },
                onProfileSelected = { vm.setAppPreset(packageName, it) },
                onFanCurveSelected = {
                    vm.setAppFanCurve(packageName, it)
                },
                onJoystickSelected = { vm.setAppJoystickProfile(packageName, it) },
                onButtonLayoutSelected = { vm.setAppButtonLayout(packageName, it) },
                onPerformanceSelected = { vm.setAppPerformanceProfile(packageName, it) },
                onProfileEdit = { editingPresetId = it },
                onAddProfile = {
                    editingPresetId = vm.addPreset(resources.getString(R.string.new_preset))
                },
                onFanCurveEdit = { editingProfileId = it },
                onAddFanCurve = {
                    editingProfileId = vm.addFanCurve(resources.getString(R.string.new_fan_curve))
                },
                onJoystickEdit = { editingJoystickProfileId = it },
                onAddJoystick = {
                    editingJoystickProfileId = vm.addJoystickProfile(
                        resources.getString(JoystickR.string.joystick_new_profile)
                    )
                },
                onButtonLayoutEdit = { editingButtonLayoutProfileId = it },
                onAddButtonLayout = {
                    editingButtonLayoutProfileId = vm.addButtonLayoutProfile(
                        resources.getString(R.string.new_button_layout),
                    )
                },
                onPerformanceEdit = { editingPerformanceProfileId = it },
                onAddPerformance = {
                    vm.addPerformanceProfile(
                        resources.getString(R.string.new_performance_profile)
                    )?.let { editingPerformanceProfileId = it }
                },
            )
        },
        accessContent = {
            AuthorizationManagementSection(
                state = AuthorizationUiState(
                    telemetryOverlayEnabled = state.overlayEnabled,
                    autoStartEnabled = state.autoStartEnabled,
                    profileSwitchToastsEnabled = state.profileSwitchToastsEnabled,
                    usbThermalDisabled = state.usbThermalDisabled,
                    thermalProtectionDisabled = state.thermalProtectionDisabled,
                    rootGranted = hasRoot,
                    overlayPermissionGranted = overlayPermissionGranted,
                    notificationsEnabled = notificationsEnabled,
                ),
                onTelemetryOverlayClick = {
                    if (!overlayPermissionGranted) {
                        context.startActivity(
                            Intent(context, OverlayPermissionActivity::class.java)
                        )
                    } else {
                        vm.setOverlayEnabled(!state.overlayEnabled)
                    }
                },
                onTelemetryOverlayEnabledChange = { checked ->
                    if (checked && !overlayPermissionGranted) {
                        context.startActivity(
                            Intent(context, OverlayPermissionActivity::class.java)
                        )
                    } else {
                        vm.setOverlayEnabled(checked)
                    }
                },
                onAutoStartEnabledChange = vm::setAutoStartEnabled,
                onProfileSwitchToastsEnabledChange = { enabled ->
                    vm.setProfileSwitchToastsEnabled(enabled)
                    onProfileSwitchToastsEnabled(enabled)
                },
                onUsbThermalDisabledChange = vm::setUsbThermalDisabled,
                onThermalProtectionDisabledChange = vm::setThermalProtectionDisabled,
                onRefreshRoot = onRefreshRoot,
                onOpenKernelSu = { context.openKernelSu() },
                onOpenAppInfo = { context.openAppInfo() },
                onOpenOverlaySettings = { context.openOverlaySettings() },
                onOpenNotificationSettings = { context.openFanNotificationSettings() },
                autoStartModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[0])
                    .focusProperties {
                        up = FocusRequester.Default
                        down = authorizationFocusRequesters[1]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                rootModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[1])
                    .focusProperties {
                        up = authorizationFocusRequesters[0]
                        down = authorizationFocusRequesters[2]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                kernelSuModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[2])
                    .focusProperties {
                        up = authorizationFocusRequesters[1]
                        down = authorizationFocusRequesters[3]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                telemetryOverlayModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[3])
                    .focusProperties {
                        up = authorizationFocusRequesters[2]
                        down = authorizationFocusRequesters[4]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                overlayModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[4])
                    .focusProperties {
                        up = authorizationFocusRequesters[3]
                        down = authorizationFocusRequesters[5]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                appInfoModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[5])
                    .focusProperties {
                        up = authorizationFocusRequesters[4]
                        down = authorizationFocusRequesters[6]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                profileSwitchToastsModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[6])
                    .focusProperties {
                        up = authorizationFocusRequesters[5]
                        down = authorizationFocusRequesters[7]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                notificationsModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[7])
                    .focusProperties {
                        up = authorizationFocusRequesters[6]
                        down = authorizationFocusRequesters[8]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                usbThermalModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[8])
                    .focusProperties {
                        up = authorizationFocusRequesters[7]
                        down = authorizationFocusRequesters[9]
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
                thermalProtectionModifier = Modifier
                    .focusRequester(authorizationFocusRequesters[9])
                    .focusProperties {
                        up = authorizationFocusRequesters[8]
                        down = githubFocusRequester
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
        footer = {
            AppFooter(
                linkModifier = Modifier
                    .focusRequester(githubFocusRequester)
                    .focusProperties {
                        up = authorizationFocusRequesters[9]
                        down = FocusRequester.Default
                        left = FocusRequester.Default
                        right = FocusRequester.Default
                    },
            )
        },
    )

    exportListKind?.let { kind ->
        val choices = when (kind) {
            ExportListKind.PRESET -> state.presetConfig.catalog.presets.map {
                ExportChoice(it.id, it.name)
            }
            ExportListKind.FAN -> state.fanConfig.catalog.profiles.map {
                ExportChoice(it.id, it.displayName(context))
            }
            ExportListKind.JOYSTICK -> state.joystickProfiles.profiles.map {
                ExportChoice(it.id, it.name)
            }
            ExportListKind.BUTTON_LAYOUT -> state.buttonLayoutProfiles.profiles.map {
                ExportChoice(it.id, it.name)
            }
            ExportListKind.PERFORMANCE -> state.performanceProfiles.profiles
                .filterNot { it.isStock }
                .map {
                ExportChoice(it.id, it.displayName(context))
            }
        }
        ExportSelectionDialog(
            choices = choices,
            onExport = { selectedIds ->
                pendingExportFiles = when (kind) {
                    ExportListKind.PRESET -> state.presetConfig.catalog.presets
                        .filter { it.id in selectedIds }
                        .map { preset ->
                            val fanCurve = state.fanConfig.catalog.profile(preset.fanCurveId)
                                ?.let { it.displayName(context) to it }
                            val joystick = state.joystickProfiles.profile(preset.joystickId)
                            val buttonLayout = state.buttonLayoutProfiles
                                .profile(preset.buttonLayoutId)
                            val performance = state.performanceProfiles
                                .profile(preset.performanceProfileId)
                                ?.takeUnless { it.isStock }
                                ?.let { it.displayName(context) to it }
                            PendingExportFile(
                                preset.name,
                                ControlItemJson.encodePreset(
                                    preset = preset,
                                    fanCurve = fanCurve,
                                    joystick = joystick,
                                    buttonLayout = buttonLayout,
                                    performance = performance,
                                ),
                            )
                        }
                    ExportListKind.FAN -> state.fanConfig.catalog.profiles
                        .filter { it.id in selectedIds }
                        .map {
                            val name = it.displayName(context)
                            PendingExportFile(name, ControlItemJson.encodeFanCurve(name, it))
                        }
                    ExportListKind.JOYSTICK -> state.joystickProfiles.profiles
                        .filter { it.id in selectedIds }
                        .map { PendingExportFile(it.name, ControlItemJson.encodeJoystick(it)) }
                    ExportListKind.BUTTON_LAYOUT -> state.buttonLayoutProfiles.profiles
                        .filter { it.id in selectedIds }
                        .map {
                            PendingExportFile(it.name, ControlItemJson.encodeButtonLayout(it))
                        }
                    ExportListKind.PERFORMANCE -> state.performanceProfiles.profiles
                        .filterNot { it.isStock }
                        .filter { it.id in selectedIds }
                        .map {
                            val name = it.displayName(context)
                            PendingExportFile(name, ControlItemJson.encodePerformance(name, it))
                        }
                }
                exportListKind = null
                exportItemsLauncher.launch(controlItemsExportDirectoryIntent())
            },
            onDismiss = { exportListKind = null },
        )
    }

    editingProfileId?.let { profileId ->
        val profile = state.fanConfig.catalog.profile(profileId) ?: return@let
        FanCurveEditorDialog(
            profileId = profile.id,
            profileName = profile.displayName(context),
            points = profile.points,
            defaultPoints = profile.defaultPoints,
            currentTempC = thermal.controlTempC,
            onPointsChanged = { vm.setFanCurve(profile.id, it) },
            onSetDefault = { vm.setFanCurveAsDefault(profile.id, it) },
            onReset = { vm.resetFanCurve(profile.id) },
            onRename = { vm.renameFanCurve(profile.id, it) },
            onDelete = {
                vm.deleteFanCurve(profile.id)
                restoreFanCurveFocusId = profile.id
                editingProfileId = null
            },
            onDismiss = {
                restoreFanCurveFocusId = profile.id
                editingProfileId = null
            },
        )
    }

    editingPresetId?.let { presetId ->
        val preset = state.presetConfig.catalog.preset(presetId) ?: return@let
        PresetEditorDialog(
            preset = preset,
            fanCurveName = fanCurveName(preset.fanCurveId),
            fanCurveChoices = buildList {
                add(PresetFanCurveChoice(id = null, name = offName))
                state.fanConfig.catalog.profiles.forEach { profile ->
                    add(
                        PresetFanCurveChoice(
                            id = profile.id,
                            name = profile.displayName(context),
                        )
                    )
                }
            },
            onFanCurveSelected = {
                vm.setPresetFanCurve(preset.id, it)
            },
            onFanCurveEdit = { editingProfileId = it },
            onAddFanCurve = {
                editingProfileId = vm.addFanCurve(resources.getString(R.string.new_fan_curve))
            },
            joystickProfileName = joystickProfileName(preset.joystickId),
            joystickChoices = buildList {
                add(PresetJoystickChoice(id = null, name = joystickOffName))
                state.joystickProfiles.profiles.forEach { profile ->
                    add(PresetJoystickChoice(id = profile.id, name = profile.name))
                }
            },
            onJoystickSelected = { vm.setPresetJoystickProfile(preset.id, it) },
            onJoystickEdit = { editingJoystickProfileId = it },
            onAddJoystick = {
                editingJoystickProfileId = vm.addJoystickProfile(
                    resources.getString(JoystickR.string.joystick_new_profile)
                )
            },
            buttonLayoutName = buttonLayoutProfileName(preset.buttonLayoutId),
            buttonLayoutChoices = buildList {
                add(PresetButtonLayoutChoice(null, unmanagedButtonLayoutName))
                state.buttonLayoutProfiles.profiles.forEach { profile ->
                    add(PresetButtonLayoutChoice(profile.id, profile.name))
                }
            },
            onButtonLayoutSelected = { vm.setPresetButtonLayout(preset.id, it) },
            onButtonLayoutEdit = { editingButtonLayoutProfileId = it },
            onAddButtonLayout = {
                editingButtonLayoutProfileId = vm.addButtonLayoutProfile(
                    resources.getString(R.string.new_button_layout),
                )
            },
            performanceProfileName = performanceProfileName(preset.performanceProfileId),
            performanceChoices = buildList {
                state.performanceProfiles.profiles.forEach { profile ->
                    add(
                        PresetPerformanceChoice(
                            id = profile.id,
                            name = profile.displayName(context),
                        )
                    )
                }
            },
            onPerformanceSelected = {
                vm.setPresetPerformanceProfile(preset.id, it)
            },
            onPerformanceEdit = { editingPerformanceProfileId = it },
            onAddPerformance = {
                vm.addPerformanceProfile(
                    resources.getString(R.string.new_performance_profile)
                )?.let { editingPerformanceProfileId = it }
            },
            onRename = { vm.renamePreset(preset.id, it) },
            onDelete = {
                vm.deletePreset(preset.id)
                restorePresetFocusId = preset.id
                editingPresetId = null
            },
            onDismiss = {
                restorePresetFocusId = preset.id
                editingPresetId = null
            },
        )
    }

    editingPerformanceProfileId?.let { profileId ->
        val profile = state.performanceProfiles.profile(profileId) ?: return@let
        PerformanceProfileEditorDialog(
            profile = profile,
            policies = state.performanceProfiles.policies,
            onSave = { name, frequencies ->
                vm.updatePerformanceProfile(profile.id, name, frequencies)
                restorePerformanceFocusId = profile.id
                editingPerformanceProfileId = null
            },
            onDelete = {
                vm.deletePerformanceProfile(profile.id)
                restorePerformanceFocusId = profile.id
                editingPerformanceProfileId = null
            },
            onDismiss = {
                restorePerformanceFocusId = profile.id
                editingPerformanceProfileId = null
            },
        )
    }

    editingButtonLayoutProfileId?.let { profileId ->
        val profile = state.buttonLayoutProfiles.profile(profileId) ?: return@let
        ButtonLayoutProfileEditorDialog(
            profile = profile,
            onLayoutSelected = { vm.setButtonLayout(profile.id, it) },
            onM1Selected = { vm.setButtonLayoutM1(profile.id, it) },
            onM2Selected = { vm.setButtonLayoutM2(profile.id, it) },
            onTriggerModeSelected = { vm.setButtonLayoutTriggerMode(profile.id, it) },
            onRename = { vm.renameButtonLayoutProfile(profile.id, it) },
            onDelete = {
                vm.deleteButtonLayoutProfile(profile.id)
                restoreButtonLayoutFocusId = profile.id
                editingButtonLayoutProfileId = null
            },
            onDismiss = {
                restoreButtonLayoutFocusId = profile.id
                editingButtonLayoutProfileId = null
            },
        )
    }

    editingJoystickProfileId?.let { profileId ->
        val profile = joystickProfileUi(profileId) ?: return@let
        JoystickProfileEditorDialog(
            profile = profile,
            onModeSelected = { mode ->
                vm.setJoystickMode(profile.id, mode)
                if (mode == JoystickRgbMode.AMBILIGHT) {
                    context.startActivity(MediaProjectionActivity.createIntent(context))
                }
            },
            onColorSelected = { red, green, blue ->
                vm.setJoystickColor(profile.id, red, green, blue)
            },
            onBrightnessSelected = { vm.setJoystickBrightness(profile.id, it) },
            onRename = { vm.renameJoystickProfile(profile.id, it) },
            onDelete = {
                vm.deleteJoystickProfile(profile.id)
                restoreJoystickFocusId = profile.id
                editingJoystickProfileId = null
            },
            onDismiss = {
                restoreJoystickFocusId = profile.id
                editingJoystickProfileId = null
            },
        )
    }

}

@Composable
private fun FanCurveEditorDialog(
    profileId: String,
    profileName: String,
    points: List<FanCurvePoint>,
    defaultPoints: List<FanCurvePoint>,
    currentTempC: Double,
    onPointsChanged: (List<FanCurvePoint>) -> Unit,
    onSetDefault: (List<FanCurvePoint>) -> Unit,
    onReset: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = remember(profileId) {
        mutableStateListOf<FanCurvePoint>().apply { addAll(points.sortedBy { it.tempC }) }
    }
    var selectedIndex by remember(profileId) { mutableIntStateOf(-1) }
    var showRenameDialog by remember(profileId) { mutableStateOf(false) }
    var renameDraft by remember(profileId) { mutableStateOf(profileName) }
    var showDeleteDialog by remember(profileId) { mutableStateOf(false) }
    LaunchedEffect(profileName, showRenameDialog) {
        if (!showRenameDialog) renameDraft = profileName
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_square),
                            contentDescription = stringResource(R.string.rename_curve),
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.delete_curve),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.weight(1f)) {
                    val compact = maxWidth < 700.dp
                    val graph: @Composable (Modifier) -> Unit = { modifier ->
                        CurveGraphPanel(
                            modifier = modifier,
                            points = draft,
                            selectedIndex = selectedIndex,
                            currentTempC = currentTempC,
                            onSelectedIndexChanged = { selectedIndex = it },
                            onCommit = { onPointsChanged(draft.toList()) },
                            onSetDefault = {
                                onSetDefault(draft.toList())
                            },
                            onReset = {
                                draft.clear()
                                draft.addAll(defaultPoints.sortedBy { it.tempC })
                                selectedIndex = -1
                                onReset()
                            },
                        )
                    }
                    val controls: @Composable (Modifier) -> Unit = { modifier ->
                        ControlPointList(
                            modifier = modifier,
                            points = draft,
                            selectedIndex = selectedIndex,
                            onSelectedIndexChanged = { selectedIndex = it },
                            onCommit = { onPointsChanged(draft.toList()) },
                        )
                    }

                    if (compact) {
                        Column(Modifier.fillMaxSize()) {
                            graph(
                                Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            controls(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            graph(
                                Modifier
                                    .weight(1.25f)
                                    .fillMaxHeight()
                            )
                            Spacer(Modifier.width(22.dp))
                            controls(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_curve)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.curve_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameDraft.isNotBlank()) {
                            onRename(renameDraft)
                            showRenameDialog = false
                        }
                    },
                    enabled = renameDraft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_curve)) },
            text = { Text(stringResource(R.string.delete_curve_confirmation, profileName)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CurveGraphPanel(
    modifier: Modifier,
    points: MutableList<FanCurvePoint>,
    selectedIndex: Int,
    currentTempC: Double,
    onSelectedIndexChanged: (Int) -> Unit,
    onCommit: () -> Unit,
    onSetDefault: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurveFileButton(
                onClick = onSetDefault,
                icon = { Icon(Icons.Default.BookmarkAdd, null, Modifier.size(17.dp)) },
                label = stringResource(R.string.set_as_default),
            )
            CurveFileButton(
                onClick = onReset,
                icon = { Icon(Icons.Default.Restore, null, Modifier.size(17.dp)) },
                label = stringResource(R.string.reset),
            )
        }
        Spacer(Modifier.height(10.dp))
        CurveGraph(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            points = points,
            selectedIndex = selectedIndex,
            currentTempC = currentTempC,
            onSelectedIndexChanged = onSelectedIndexChanged,
            onCommit = onCommit,
        )
    }
}

@Composable
private fun CurveFileButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp),
    ) {
        icon()
        Spacer(Modifier.width(7.dp))
        Text(label)
    }
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private fun Intent?.selectedDocumentUris(): List<Uri> {
    val intent = this ?: return emptyList()
    return buildList {
        intent.clipData?.let { clips ->
            repeat(clips.itemCount) { index -> add(clips.getItemAt(index).uri) }
        }
        intent.data?.let(::add)
    }.distinct()
}

private fun controlItemsImportIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"),
        )
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDocumentUri())
    }

private fun controlItemsExportDirectoryIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDocumentUri())
    }

private fun downloadsDocumentUri(): Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Download",
)

private fun Context.exportControlFiles(
    treeUri: Uri,
    files: List<PendingExportFile>,
): Pair<Int, Int> {
    val resolver = contentResolver
    val parentUri = runCatching {
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    }.getOrElse { return 0 to files.size }
    val usedNames = resolver.childDisplayNames(treeUri).toMutableSet()
    var exported = 0
    var failed = 0
    files.forEach { file ->
        val name = uniqueJsonFileName(file.itemName, usedNames)
        val success = runCatching {
            val uri = DocumentsContract.createDocument(
                resolver,
                parentUri,
                "application/json",
                name,
            ) ?: error("Unable to create export file")
            resolver.openOutputStream(uri, "wt")
                ?.bufferedWriter()
                ?.use { it.write(file.json) }
                ?: error("Unable to open export file")
        }.isSuccess
        if (success) {
            exported++
            usedNames += name
        } else {
            failed++
        }
    }
    return exported to failed
}

private fun android.content.ContentResolver.childDisplayNames(treeUri: Uri): Set<String> =
    runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }.orEmpty()
    }.getOrDefault(emptySet())

private fun uniqueJsonFileName(itemName: String, usedNames: Set<String>): String {
    val sanitized = itemName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .trim('.')
        .ifBlank { "item" }
    val base = if (sanitized.endsWith(".json", ignoreCase = true)) {
        sanitized.dropLast(5).ifBlank { "item" }
    } else {
        sanitized
    }
    var suffix = 0
    var candidate: String
    do {
        candidate = if (suffix == 0) "$base.json" else "$base.$suffix.json"
        suffix++
    } while (usedNames.any { it.equals(candidate, ignoreCase = true) })
    return candidate
}

private fun Context.areFanNotificationsEnabled(): Boolean {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    val channel = getSystemService(NotificationManager::class.java)
        .getNotificationChannel(SystemControlService.CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

private fun Context.openFanNotificationSettings() {
    val channelSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, SystemControlService.CHANNEL_ID)
    }
    val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    startActivity(
        if (channelSettings.resolveActivity(packageManager) != null) {
            channelSettings
        } else {
            appSettings
        }
    )
}

private fun Context.openAppInfo() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )
}

private fun Context.openOverlaySettings() {
    val appOverlaySettings = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", packageName, null),
    )
    val generalOverlaySettings = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    startActivity(
        if (appOverlaySettings.resolveActivity(packageManager) != null) {
            appOverlaySettings
        } else {
            generalOverlaySettings
        }
    )
}

private fun Context.openKernelSu() {
    val managerIntent = listOf(
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext",
    ).firstNotNullOfOrNull { packageName ->
        packageManager.getLaunchIntentForPackage(packageName)
    }
    if (managerIntent != null) {
        startActivity(managerIntent)
    } else {
        Toast.makeText(this, R.string.kernelsu_not_found, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AppFooter(
    modifier: Modifier = Modifier,
    linkModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val githubUrl = stringResource(R.string.github_url)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "RetroControl v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = linkModifier
                .clip(RoundedCornerShape(8.dp))
                .bringIntoViewOnFocus()
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                    )
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.github),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.open_github),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurveGraph(
    modifier: Modifier,
    points: MutableList<FanCurvePoint>,
    selectedIndex: Int,
    currentTempC: Double,
    onSelectedIndexChanged: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    var graphSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val hitRadius = with(density) { 24.dp.toPx() }
    val padLeft = with(density) { 45.dp.toPx() }
    val padRight = with(density) { 18.dp.toPx() }
    val padTop = with(density) { 18.dp.toPx() }
    val padBottom = with(density) { 34.dp.toPx() }

    fun graphWidth(size: IntSize) = (size.width - padLeft - padRight).coerceAtLeast(1f)
    fun graphHeight(size: IntSize) = (size.height - padTop - padBottom).coerceAtLeast(1f)
    fun pointOffset(point: FanCurvePoint, size: IntSize): Offset = Offset(
        x = padLeft + (point.tempC - 20f) / 80f * graphWidth(size),
        y = padTop + graphHeight(size) -
            point.speedPercent / 100f * graphHeight(size),
    )
    fun positionToPoint(position: Offset, index: Int, size: IntSize): FanCurvePoint {
        val rawTemp = (20f + ((position.x - padLeft) / graphWidth(size)) * 80f).roundToInt()
        val minTemp = if (index > 0) points[index - 1].tempC + 1 else 20
        val maxTemp = if (index < points.lastIndex) points[index + 1].tempC - 1 else 100
        return FanCurvePoint(
            tempC = rawTemp.coerceIn(minTemp, maxTemp),
            speedPercent = (
                ((padTop + graphHeight(size) - position.y) / graphHeight(size) * 100f) /
                    5f
                )
                .roundToInt()
                .times(5)
                .coerceIn(0, 100),
        )
    }
    fun nearestPoint(position: Offset, size: IntSize): Int =
        points.indices.minByOrNull { index ->
            val offset = pointOffset(points[index], size)
            hypot((offset.x - position.x).toDouble(), (offset.y - position.y).toDouble())
        }?.takeIf { index ->
            val offset = pointOffset(points[index], size)
            hypot((offset.x - position.x).toDouble(), (offset.y - position.y).toDouble()) <=
                hitRadius
        } ?: -1
    fun addPoint(position: Offset, size: IntSize) {
        if (
            position.x !in padLeft..(size.width - padRight) ||
            position.y !in padTop..(size.height - padBottom)
        ) return
        val temp = (20f + ((position.x - padLeft) / graphWidth(size)) * 80f)
            .roundToInt()
            .coerceIn(20, 100)
        if (points.any { it.tempC == temp }) return
        val speedPercent = (
            ((padTop + graphHeight(size) - position.y) / graphHeight(size) * 100f) / 5f
            )
            .roundToInt()
            .times(5)
            .coerceIn(0, 100)
        val updated = (points + FanCurvePoint(temp, speedPercent)).sortedBy { it.tempC }
        points.clear()
        points.addAll(updated)
        onSelectedIndexChanged(points.indexOfFirst { it.tempC == temp })
        onCommit()
    }

    val displayedPoints = points.toList()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onSizeChanged { graphSize = it }
            .pointerInput(graphSize, points.size) {
                if (graphSize == IntSize.Zero) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val size = graphSize
                    val hit = nearestPoint(down.position, size)
                    val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    }
                    if (dragStart == null) {
                        if (hit >= 0) {
                            onSelectedIndexChanged(hit)
                        } else {
                            addPoint(down.position, size)
                        }
                    } else if (hit >= 0 && hit in points.indices) {
                        onSelectedIndexChanged(hit)
                        points[hit] = positionToPoint(dragStart.position, hit, size)
                        onCommit()
                        drag(dragStart.id) { change ->
                            if (hit in points.indices) {
                                points[hit] = positionToPoint(change.position, hit, size)
                                onCommit()
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        val graphW = size.width - padLeft - padRight
        val graphH = size.height - padTop - padBottom
        fun tempToX(temp: Float) = padLeft + (temp - 20f) / 80f * graphW
        fun percentToY(percent: Float) = padTop + graphH - percent / 100f * graphH

        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(170, 210, 210, 210)
            textSize = 20f
            isAntiAlias = true
        }
        for (temp in 20..100 step 10) {
            val x = tempToX(temp.toFloat())
            drawLine(
                onSurface.copy(alpha = 0.09f),
                Offset(x, padTop),
                Offset(x, size.height - padBottom),
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$temp°",
                x - 12f,
                size.height - 7f,
                labelPaint,
            )
        }
        for (percent in 0..100 step 25) {
            val y = percentToY(percent.toFloat())
            drawLine(
                onSurface.copy(alpha = 0.09f),
                Offset(padLeft, y),
                Offset(size.width - padRight, y),
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$percent%",
                3f,
                y + 6f,
                labelPaint,
            )
        }

        if (displayedPoints.isNotEmpty()) {
            val first = displayedPoints.first()
            val last = displayedPoints.last()
            val dashedPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            val extensionColor = primary.copy(alpha = 0.48f)
            if (first.tempC > 20) {
                val firstX = tempToX(first.tempC.toFloat())
                val zeroY = percentToY(0f)
                drawLine(
                    extensionColor,
                    Offset(padLeft, zeroY),
                    Offset(firstX, zeroY),
                    strokeWidth = 2.5f,
                    pathEffect = dashedPathEffect,
                )
                drawLine(
                    extensionColor,
                    Offset(firstX, zeroY),
                    Offset(firstX, percentToY(first.speedPercent.toFloat())),
                    strokeWidth = 2.5f,
                    pathEffect = dashedPathEffect,
                )
            }
            if (last.tempC < 100) {
                val lastY = percentToY(last.speedPercent.toFloat())
                drawLine(
                    extensionColor,
                    Offset(tempToX(last.tempC.toFloat()), lastY),
                    Offset(size.width - padRight, lastY),
                    strokeWidth = 2.5f,
                    pathEffect = dashedPathEffect,
                )
            }
        }

        if (displayedPoints.size >= 2) {
            val path = Path()
            displayedPoints.forEachIndexed { index, point ->
                val offset = Offset(
                    tempToX(point.tempC.toFloat()),
                    percentToY(point.speedPercent.toFloat()),
                )
                if (index == 0) path.moveTo(offset.x, offset.y)
                else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, primary, style = Stroke(5f, cap = StrokeCap.Round))
        }
        displayedPoints.forEachIndexed { index, point ->
            val center = Offset(
                tempToX(point.tempC.toFloat()),
                percentToY(point.speedPercent.toFloat()),
            )
            val selected = index == selectedIndex
            drawCircle(primary.copy(alpha = 0.24f), if (selected) 22f else 17f, center)
            drawCircle(primary, if (selected) 13f else 10f, center)
            drawCircle(Color.White, 4.5f, center)
        }
        if (currentTempC in 20.0..100.0) {
            val x = tempToX(currentTempC.toFloat())
            drawLine(
                error.copy(alpha = 0.75f),
                Offset(x, padTop),
                Offset(x, size.height - padBottom),
                strokeWidth = 2f,
            )
        }
    }
}

@Composable
private fun ControlPointList(
    modifier: Modifier,
    points: MutableList<FanCurvePoint>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.control_points),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        points.forEachIndexed { index, point ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectedIndexChanged(index) },
                shape = RoundedCornerShape(16.dp),
                color = if (index == selectedIndex) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.control_point_value,
                                point.tempC,
                                formatFanPercent(point.speedPercent),
                            ),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (points.size > 2) {
                            IconButton(
                                onClick = {
                                    points.removeAt(index)
                                    onSelectedIndexChanged(-1)
                                    onCommit()
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                    Slider(
                        value = point.tempC.toFloat(),
                        onValueChange = { value ->
                            val min = if (index > 0) points[index - 1].tempC + 1 else 20
                            val max = if (index < points.lastIndex) {
                                points[index + 1].tempC - 1
                            } else {
                                100
                            }
                            points[index] = point.copy(
                                tempC = value.roundToInt().coerceIn(min, max)
                            )
                            onSelectedIndexChanged(index)
                            onCommit()
                        },
                        valueRange = 20f..100f,
                        modifier = Modifier.height(28.dp),
                    )
                    Slider(
                        value = point.speedPercent.toFloat(),
                        onValueChange = { value ->
                            points[index] = point.copy(
                                speedPercent = (value / 5f)
                                    .roundToInt()
                                    .times(5)
                                    .coerceIn(0, 100)
                            )
                            onSelectedIndexChanged(index)
                            onCommit()
                        },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.height(28.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val widestGap = (0 until points.lastIndex)
                    .maxByOrNull { points[it + 1].tempC - points[it].tempC }
                val temp = widestGap?.let {
                    (points[it].tempC + points[it + 1].tempC) / 2
                } ?: 50
                val speedPercent = widestGap?.let {
                    (points[it].speedPercent + points[it + 1].speedPercent) / 2
                } ?: 50
                if (points.none { it.tempC == temp }) {
                    val updated = (points + FanCurvePoint(temp, speedPercent))
                        .sortedBy { it.tempC }
                    points.clear()
                    points.addAll(updated)
                    onSelectedIndexChanged(points.indexOfFirst { it.tempC == temp })
                    onCommit()
                }
            },
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_point))
        }
        Spacer(Modifier.height(8.dp))
    }
}
