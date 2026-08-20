@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.data.ControlPreset
import com.mmax.retrocontrol.designsystem.SecondaryMenuList
import com.mmax.retrocontrol.designsystem.SecondaryMenuListItem
import com.mmax.retrocontrol.designsystem.SecondaryMenuSelectableListItem
import com.mmax.retrocontrol.designsystem.settingsSegmentedShapes
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus

data class PresetListItemUiState(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val fanCurveName: String,
    val joystickProfileName: String,
    val buttonLayoutName: String,
    val performanceProfileName: String,
)

data class PresetFanCurveChoice(
    val id: String?,
    val name: String,
)

data class PresetJoystickChoice(
    val id: String?,
    val name: String,
)

data class PresetPerformanceChoice(
    val id: String?,
    val name: String,
)

data class PresetButtonLayoutChoice(
    val id: String?,
    val name: String,
)

private val PresetListItemUiState.summary: String
    get() = "$fanCurveName · $joystickProfileName · $buttonLayoutName · $performanceProfileName"

@Composable
fun PresetManagementSection(
    presets: List<PresetListItemUiState>,
    onPresetClick: (String) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
) {
    SecondaryMenuList {
        presets.forEachIndexed { index, preset ->
            PresetListItem(
                preset = preset,
                index = index,
                count = presets.size,
                onClick = { onPresetClick(preset.id) },
                modifier = itemModifier(index),
            )
        }
    }
}

@Composable
fun GlobalPresetSelectionSection(
    presets: List<PresetListItemUiState>,
    selectedPresetId: String,
    onPresetSelected: (String) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
) {
    SecondaryMenuList {
        presets.forEachIndexed { index, preset ->
            val selected = preset.id == selectedPresetId
            SecondaryMenuSelectableListItem(
                selected = selected,
                index = index,
                count = presets.size,
                onClick = { onPresetSelected(preset.id) },
                content = {
                    Text(
                        text = preset.name,
                        style = if (selected) {
                            MaterialTheme.typography.bodyLargeEmphasized
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = { Text(preset.summary) },
                trailingContent = {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                modifier = itemModifier(index),
            )
        }
    }
}

@Composable
fun AddPresetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.add_preset)) },
    )
}

@Composable
private fun PresetListItem(
    preset: PresetListItemUiState,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryMenuListItem(
        index = index,
        count = count,
        onClick = onClick,
        content = {
            Text(
                text = preset.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(preset.summary) },
        modifier = modifier,
    )
}

@Composable
fun PresetEditorDialog(
    preset: ControlPreset,
    fanCurveName: String,
    fanCurveChoices: List<PresetFanCurveChoice>,
    onFanCurveSelected: (String?) -> Unit,
    onFanCurveEdit: (String) -> Unit,
    onAddFanCurve: () -> Unit,
    joystickProfileName: String,
    joystickChoices: List<PresetJoystickChoice>,
    onJoystickSelected: (String?) -> Unit,
    onJoystickEdit: (String) -> Unit,
    onAddJoystick: () -> Unit,
    buttonLayoutName: String,
    buttonLayoutChoices: List<PresetButtonLayoutChoice>,
    onButtonLayoutSelected: (String?) -> Unit,
    onButtonLayoutEdit: (String) -> Unit,
    onAddButtonLayout: () -> Unit,
    performanceProfileName: String,
    performanceChoices: List<PresetPerformanceChoice>,
    onPerformanceSelected: (String?) -> Unit,
    onPerformanceEdit: (String) -> Unit,
    onAddPerformance: () -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var controlPicker by remember(preset.id) { mutableStateOf<String?>(null) }
    var showRename by remember(preset.id) { mutableStateOf(false) }
    var renameDraft by remember(preset.id, preset.name) { mutableStateOf(preset.name) }
    var showDelete by remember(preset.id) { mutableStateOf(false) }

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
                        text = preset.name,
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
                            contentDescription = stringResource(R.string.rename_preset),
                        )
                    }
                    IconButton(
                        onClick = onExport,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.export_items),
                        )
                    }
                    if (!preset.isDefault) {
                        IconButton(
                            onClick = { showDelete = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = stringResource(R.string.delete_preset),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    ProfileControlSetting(
                        title = stringResource(R.string.preset_fan_curve),
                        summary = fanCurveName,
                        onClick = { controlPicker = "fan" },
                        index = 0,
                        count = 4,
                    )
                    ProfileControlSetting(
                        title = stringResource(R.string.control_joystick),
                        summary = joystickProfileName,
                        onClick = { controlPicker = "joystick" },
                        index = 1,
                        count = 4,
                    )
                    ProfileControlSetting(
                        title = stringResource(R.string.control_button_layout),
                        summary = buttonLayoutName,
                        onClick = { controlPicker = "button" },
                        index = 2,
                        count = 4,
                    )
                    ProfileControlSetting(
                        title = stringResource(R.string.preset_performance_profile),
                        summary = performanceProfileName,
                        onClick = { controlPicker = "performance" },
                        index = 3,
                        count = 4,
                    )
                }
            }
        }
    }

    controlPicker?.let { picker ->
        when (picker) {
            "fan" -> ChoiceDialog(
                title = stringResource(R.string.select_fan_curve),
                choices = fanCurveChoices.map { AppProfileChoice(it.id, it.name) },
                selectedId = preset.fanCurveId,
                showRadio = true,
                addLabel = stringResource(R.string.add_fan_curve),
                onSelected = onFanCurveSelected,
                onItemClick = { id -> id?.let(onFanCurveEdit) },
                onAdd = onAddFanCurve,
                onDismiss = { controlPicker = null },
            )
            "joystick" -> ChoiceDialog(
                title = stringResource(R.string.select_joystick_profile),
                choices = joystickChoices.map { AppProfileChoice(it.id, it.name) },
                selectedId = preset.joystickId,
                showRadio = true,
                addLabel = stringResource(R.string.add_preset),
                onSelected = onJoystickSelected,
                onItemClick = { id -> id?.let(onJoystickEdit) },
                onAdd = onAddJoystick,
                onDismiss = { controlPicker = null },
            )
            "performance" -> ChoiceDialog(
                title = stringResource(R.string.select_performance_profile),
                choices = performanceChoices.map { AppProfileChoice(it.id, it.name) },
                selectedId = preset.performanceProfileId,
                showRadio = true,
                addLabel = stringResource(R.string.add_performance_profile),
                onSelected = onPerformanceSelected,
                onItemClick = { id -> id?.let(onPerformanceEdit) },
                onAdd = onAddPerformance,
                onDismiss = { controlPicker = null },
            )
            else -> ChoiceDialog(
                title = stringResource(R.string.select_button_layout),
                choices = buttonLayoutChoices.map { AppProfileChoice(it.id, it.name) },
                selectedId = preset.buttonLayoutId,
                showRadio = true,
                addLabel = stringResource(R.string.add_button_layout),
                onSelected = onButtonLayoutSelected,
                onItemClick = { id -> id?.let(onButtonLayoutEdit) },
                onAdd = onAddButtonLayout,
                onDismiss = { controlPicker = null },
            )
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename_preset)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.preset_name)) },
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
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRename = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showDelete) {
        DeletePresetConfirmation(
            name = preset.name,
            onConfirm = onDelete,
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun ProfileControlSetting(
    title: String,
    summary: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = settingsSegmentedShapes(index = index, count = count),
        content = { Text(title) },
        supportingContent = summary.takeIf(String::isNotEmpty)?.let { value -> { Text(value) } },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyPresetSetting(
    title: String,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        onClick = {},
        shapes = settingsSegmentedShapes(index = index, count = count),
        content = { Text(title) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeletePresetConfirmation(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_preset)) },
        text = { Text(stringResource(R.string.delete_preset_confirmation, name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
