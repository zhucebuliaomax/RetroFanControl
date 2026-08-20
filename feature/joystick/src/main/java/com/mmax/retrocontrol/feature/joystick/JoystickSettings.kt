@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.mmax.retrocontrol.feature.joystick

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mmax.retrocontrol.designsystem.SecondaryMenuList
import com.mmax.retrocontrol.designsystem.SecondaryMenuListItem
import com.mmax.retrocontrol.designsystem.SettingsListDialog
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus
import kotlin.math.roundToInt

enum class JoystickRgbMode(
    @param:StringRes val labelRes: Int,
    val supportsCustomColor: Boolean = false,
) {
    OFF(R.string.joystick_mode_off),
    STATIC(R.string.joystick_mode_static, supportsCustomColor = true),
    RAINBOW(R.string.joystick_mode_rainbow),
    BREATHE(R.string.joystick_mode_breathe, supportsCustomColor = true),
    AMBILIGHT(R.string.joystick_mode_ambilight),
    BATTERY(R.string.joystick_mode_battery),
    THERMAL(R.string.joystick_mode_thermal),
    WAVE(R.string.joystick_mode_wave),
    COLOR_CYCLE(R.string.joystick_mode_color_cycle),
    METEOR(R.string.joystick_mode_meteor, supportsCustomColor = true),
    FIRE(R.string.joystick_mode_fire),
    AURORA(R.string.joystick_mode_aurora),
    OCEAN(R.string.joystick_mode_ocean),
    STARLIGHT(R.string.joystick_mode_starlight),
}

data class JoystickProfileUiState(
    val id: String,
    val name: String,
    val mode: JoystickRgbMode,
    val red: Int,
    val green: Int,
    val blue: Int,
    val brightness: Int,
)

@Composable
fun JoystickProfilesSection(
    profiles: List<JoystickProfileUiState>,
    onProfileSelected: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    offModifier: Modifier = Modifier,
    profileModifier: (Int) -> Modifier = { Modifier },
) {
    SecondaryMenuList(modifier) {
        val count = profiles.size + 1
        SecondaryMenuListItem(
            index = 0,
            count = count,
            onClick = {},
            modifier = offModifier,
            supportingContent = { Text(stringResource(R.string.joystick_off_summary)) },
            content = { Text(stringResource(R.string.joystick_off)) },
        )
        profiles.forEachIndexed { index, profile ->
            key(profile.id) {
                SecondaryMenuListItem(
                    index = index + 1,
                    count = count,
                    onClick = { onProfileSelected(profile.id) },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    },
                    modifier = profileModifier(index),
                    supportingContent = { Text(stringResource(profile.mode.labelRes)) },
                    content = {
                        Text(
                            text = profile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun AddJoystickProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.joystick_add_profile)) },
    )
}

@Composable
fun JoystickProfileEditorDialog(
    profile: JoystickProfileUiState,
    onModeSelected: (JoystickRgbMode) -> Unit,
    onColorSelected: (Int, Int, Int) -> Unit,
    onBrightnessSelected: (Int) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var showModePicker by remember(profile.id) { mutableStateOf(false) }
    var showColorPicker by remember(profile.id) { mutableStateOf(false) }
    var showRename by remember(profile.id) { mutableStateOf(false) }
    var renameDraft by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var showDelete by remember(profile.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { showRename = true },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_square),
                            contentDescription = stringResource(R.string.joystick_rename_profile),
                        )
                    }
                    IconButton(
                        onClick = { showDelete = true },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.joystick_delete_profile),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    SegmentedListItem(
                        onClick = { showModePicker = true },
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        },
                        supportingContent = { Text(stringResource(profile.mode.labelRes)) },
                        content = { Text(stringResource(R.string.joystick_rgb)) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    )
                    BrightnessSetting(
                        brightness = profile.brightness,
                        enabled = profile.mode != JoystickRgbMode.OFF,
                        onBrightnessSelected = onBrightnessSelected,
                    )
                    ColorPresetSetting(
                        profile = profile,
                        enabled = profile.mode.supportsCustomColor,
                        onColorSelected = onColorSelected,
                        onCustomColorClick = {
                            focusManager.clearFocus(force = true)
                            showColorPicker = true
                        },
                    )
                }
            }
        }
    }

    if (showModePicker) {
        RgbModeDialog(
            selectedMode = profile.mode,
            onModeSelected = onModeSelected,
            onDismiss = { showModePicker = false },
        )
    }
    if (showColorPicker) {
        ColorPickerDialog(
            initialR = profile.red,
            initialG = profile.green,
            initialB = profile.blue,
            onColorSelected = onColorSelected,
            onDismiss = { showColorPicker = false },
        )
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.joystick_rename_profile)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.joystick_profile_name)) },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(renameDraft)
                        showRename = false
                    },
                    enabled = renameDraft.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.joystick_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRename = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.joystick_cancel))
                }
            },
        )
    }
    if (showDelete) {
        DeleteProfileConfirmation(
            name = profile.name,
            onConfirm = onDelete,
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun ColorPresetSetting(
    profile: JoystickProfileUiState,
    enabled: Boolean,
    onColorSelected: (Int, Int, Int) -> Unit,
    onCustomColorClick: () -> Unit,
) {
    val swatches = remember {
        listOf(
            Triple(255, 0, 0), Triple(255, 128, 0), Triple(255, 255, 0),
            Triple(128, 255, 0), Triple(0, 255, 0), Triple(0, 255, 128),
            Triple(0, 255, 255), Triple(0, 128, 255), Triple(0, 0, 255),
            Triple(128, 0, 255), Triple(255, 0, 255), Triple(255, 0, 128),
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.48f),
        shape = ListItemDefaults.segmentedShapes(index = 2, count = 3).shape,
        color = ListItemDefaults.segmentedColors().containerColor,
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.joystick_color_presets))
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 12.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                swatches.forEach { color ->
                    val (red, green, blue) = color
                    val selected = red == profile.red &&
                        green == profile.green && blue == profile.blue
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.12f else 1f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "joystickColorPresetScale",
                    )
                    Surface(
                        onClick = { onColorSelected(red, green, blue) },
                        enabled = enabled,
                        modifier = Modifier
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .size(46.dp),
                        shape = CircleShape,
                        color = Color(red, green, blue),
                        border = BorderStroke(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {}
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCustomColorClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.joystick_custom_color_picker)) }
        }
    }
}

@Composable
private fun BrightnessSetting(
    brightness: Int,
    enabled: Boolean,
    onBrightnessSelected: (Int) -> Unit,
) {
    var sliderNode by remember { mutableIntStateOf(nodeForBrightness(brightness)) }
    var lastPreviewAt by remember { mutableStateOf(0L) }
    LaunchedEffect(brightness) { sliderNode = nodeForBrightness(brightness) }
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.48f),
        shape = ListItemDefaults.segmentedShapes(index = 1, count = 3).shape,
        color = ListItemDefaults.segmentedColors().containerColor,
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(text = stringResource(R.string.joystick_brightness))
            Spacer(Modifier.height(4.dp))
            Slider(
                value = sliderNode.toFloat(),
                onValueChange = { value ->
                    val node = value.roundToInt().coerceIn(0, BRIGHTNESS_NODE_COUNT - 1)
                    if (node != sliderNode) {
                        sliderNode = node
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPreviewAt >= LED_PREVIEW_INTERVAL_MS) {
                            lastPreviewAt = now
                            onBrightnessSelected(brightnessForNode(node))
                        }
                    }
                },
                onValueChangeFinished = {
                    onBrightnessSelected(brightnessForNode(sliderNode))
                },
                enabled = enabled,
                valueRange = 0f..(BRIGHTNESS_NODE_COUNT - 1).toFloat(),
                steps = BRIGHTNESS_NODE_COUNT - 2,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(),
            )
        }
    }
}

@Composable
private fun RgbModeDialog(
    selectedMode: JoystickRgbMode,
    onModeSelected: (JoystickRgbMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val modes = JoystickRgbMode.entries.filterNot { it == JoystickRgbMode.AMBILIGHT }
    val labels = modes.map { mode -> stringResource(mode.labelRes) }
    val selectMode: (Int) -> Unit = { index ->
        onModeSelected(modes[index])
        onDismiss()
    }
    SettingsListDialog(
        title = stringResource(R.string.joystick_rgb),
        itemCount = modes.size,
        itemLabel = labels::get,
        selectedIndex = modes.indexOf(selectedMode),
        showRadio = true,
        onSelected = selectMode,
        onItemClick = selectMode,
        cancelLabel = stringResource(R.string.joystick_cancel),
        onDismiss = onDismiss,
    )
}

@Composable
private fun ColorPickerDialog(
    initialR: Int,
    initialG: Int,
    initialB: Int,
    onColorSelected: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialR, initialG, initialB) {
        FloatArray(3).also { hsv ->
            AndroidColor.RGBToHSV(initialR, initialG, initialB, hsv)
        }
    }
    val originalColor = remember { Triple(initialR, initialG, initialB) }
    var hue by remember {
        mutableFloatStateOf(initialHsv[0])
    }
    var saturation by remember {
        mutableFloatStateOf(initialHsv[1] * 100f)
    }
    var lastPreviewAt by remember { mutableStateOf(0L) }
    var previewChanged by remember { mutableStateOf(false) }
    val sendColor: (Boolean) -> Unit = { force ->
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastPreviewAt >= LED_PREVIEW_INTERVAL_MS) {
            lastPreviewAt = now
            val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation / 100f, 1f))
            previewChanged = true
            onColorSelected(
                AndroidColor.red(color),
                AndroidColor.green(color),
                AndroidColor.blue(color),
            )
        }
    }
    val dismissWithoutSelection = {
        if (previewChanged) {
            onColorSelected(originalColor.first, originalColor.second, originalColor.third)
        }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = dismissWithoutSelection,
        title = { Text(stringResource(R.string.joystick_custom_color)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HsvChannelSlider(
                    label = "H ${hue.roundToInt()}°",
                    value = hue,
                    valueRange = 0f..360f,
                    brush = Brush.horizontalGradient(HUE_GRADIENT),
                    onValueChange = {
                        hue = it
                        sendColor(false)
                    },
                    onValueChangeFinished = { sendColor(true) },
                )
                HsvChannelSlider(
                    label = "S ${saturation.roundToInt()}%",
                    value = saturation,
                    valueRange = 0f..100f,
                    brush = Brush.horizontalGradient(
                        listOf(Color.White, hsvColor(hue, 100f)),
                    ),
                    onValueChange = {
                        saturation = it
                        sendColor(false)
                    },
                    onValueChangeFinished = { sendColor(true) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sendColor(true)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.joystick_select)) }
        },
        dismissButton = {
            TextButton(onClick = dismissWithoutSelection, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.joystick_cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun HsvChannelSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun DeleteProfileConfirmation(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.joystick_delete_profile)) },
        text = {
            Text(stringResource(R.string.joystick_delete_profile_confirmation, name))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.joystick_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.joystick_cancel))
            }
        },
    )
}

private val HUE_GRADIENT = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

private fun hsvColor(hue: Float, saturation: Float): Color = Color(
    AndroidColor.HSVToColor(
        floatArrayOf(hue, saturation.coerceIn(0f, 100f) / 100f, 1f),
    ),
)

private val BRIGHTNESS_LEVELS = intArrayOf(
    0, 10, 14, 21, 30, 42, 61, 87, 125, 179, 255,
)
private val BRIGHTNESS_NODE_COUNT = BRIGHTNESS_LEVELS.size
private const val LED_PREVIEW_INTERVAL_MS = 50L

private fun brightnessForNode(node: Int): Int =
    BRIGHTNESS_LEVELS[node.coerceIn(BRIGHTNESS_LEVELS.indices)]

private fun nodeForBrightness(brightness: Int): Int = BRIGHTNESS_LEVELS.indices.minBy { node ->
    kotlin.math.abs(BRIGHTNESS_LEVELS[node] - brightness.coerceIn(0, 255))
}
