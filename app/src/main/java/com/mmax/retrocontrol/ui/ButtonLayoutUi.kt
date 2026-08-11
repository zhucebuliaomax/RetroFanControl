@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.data.ButtonLayoutProfile
import com.mmax.retrocontrol.data.FaceButtonLayout
import com.mmax.retrocontrol.data.GamepadButtonMapping
import com.mmax.retrocontrol.data.GamepadTriggerMode
import com.mmax.retrocontrol.designsystem.SecondaryMenuList
import com.mmax.retrocontrol.designsystem.SecondaryMenuListItem
import com.mmax.retrocontrol.designsystem.SwipeToDeleteSecondaryMenuListItem
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus
import com.mmax.retrocontrol.designsystem.settingsSegmentedShapes

internal val FaceButtonLayout.labelRes: Int
    @StringRes get() = when (this) {
        FaceButtonLayout.XBOX -> R.string.button_layout_xbox
        FaceButtonLayout.NINTENDO -> R.string.button_layout_nintendo
    }

internal val GamepadButtonMapping.labelRes: Int
    @StringRes get() = when (this) {
        GamepadButtonMapping.NONE -> R.string.button_mapping_none
        GamepadButtonMapping.HOME -> R.string.button_mapping_home
        GamepadButtonMapping.SELECT -> R.string.button_mapping_select
        GamepadButtonMapping.START -> R.string.button_mapping_start
        GamepadButtonMapping.BACK -> R.string.button_mapping_back
        GamepadButtonMapping.A -> R.string.button_mapping_a
        GamepadButtonMapping.B -> R.string.button_mapping_b
        GamepadButtonMapping.X -> R.string.button_mapping_x
        GamepadButtonMapping.Y -> R.string.button_mapping_y
        GamepadButtonMapping.L1 -> R.string.button_mapping_l1
        GamepadButtonMapping.L2 -> R.string.button_mapping_l2
        GamepadButtonMapping.L3 -> R.string.button_mapping_l3
        GamepadButtonMapping.R1 -> R.string.button_mapping_r1
        GamepadButtonMapping.R2 -> R.string.button_mapping_r2
        GamepadButtonMapping.R3 -> R.string.button_mapping_r3
        GamepadButtonMapping.DOWN -> R.string.button_mapping_down
        GamepadButtonMapping.UP -> R.string.button_mapping_up
        GamepadButtonMapping.LEFT -> R.string.button_mapping_left
        GamepadButtonMapping.RIGHT -> R.string.button_mapping_right
    }

internal val GamepadTriggerMode.labelRes: Int
    @StringRes get() = when (this) {
        GamepadTriggerMode.BOTH -> R.string.trigger_mode_both
        GamepadTriggerMode.ANALOG -> R.string.trigger_mode_analog
        GamepadTriggerMode.DIGITAL -> R.string.trigger_mode_digital
    }

@Composable
fun ButtonLayoutProfilesSection(
    profiles: List<ButtonLayoutProfile>,
    onProfileSelected: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    profileModifier: (Int) -> Modifier = { Modifier },
) {
    SecondaryMenuList {
        profiles.forEachIndexed { index, profile ->
            key(profile.id) {
                var showDelete by remember(profile.id) { mutableStateOf(false) }
                val summary = stringResource(
                    R.string.button_layout_summary,
                    stringResource(profile.layout.labelRes),
                    stringResource(profile.m1.labelRes),
                    stringResource(profile.m2.labelRes),
                    stringResource(profile.triggerMode.labelRes),
                )
                if (profile.isBuiltIn) {
                    SecondaryMenuListItem(
                        index = index,
                        count = profiles.size,
                        onClick = { onProfileSelected(profile.id) },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        },
                        modifier = profileModifier(index),
                        supportingContent = { Text(summary) },
                        content = {
                            Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                } else {
                    SwipeToDeleteSecondaryMenuListItem(
                        index = index,
                        count = profiles.size,
                        onClick = { onProfileSelected(profile.id) },
                        onDeleteRequest = { showDelete = true },
                        deleteIcon = Icons.Default.Delete,
                        deleteContentDescription = stringResource(R.string.delete_button_layout),
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        },
                        modifier = profileModifier(index),
                        supportingContent = { Text(summary) },
                        content = {
                            Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
                if (showDelete) {
                    DeleteButtonLayoutConfirmation(
                        name = profile.name,
                        onConfirm = {
                            showDelete = false
                            onDeleteProfile(profile.id)
                        },
                        onDismiss = { showDelete = false },
                    )
                }
            }
        }
    }
}

@Composable
fun AddButtonLayoutProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.add_button_layout)) },
    )
}

@Composable
fun ButtonLayoutProfileEditorDialog(
    profile: ButtonLayoutProfile,
    onLayoutSelected: (FaceButtonLayout) -> Unit,
    onM1Selected: (GamepadButtonMapping) -> Unit,
    onM2Selected: (GamepadButtonMapping) -> Unit,
    onTriggerModeSelected: (GamepadTriggerMode) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picker by remember(profile.id) { mutableStateOf<String?>(null) }
    var showRename by remember(profile.id) { mutableStateOf(false) }
    var renameDraft by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var showDelete by remember(profile.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!profile.isBuiltIn) {
                        IconButton(
                            onClick = { showRename = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_edit_square),
                                contentDescription = stringResource(R.string.rename_button_layout),
                            )
                        }
                        IconButton(
                            onClick = { showDelete = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = stringResource(R.string.delete_button_layout),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    val rowCount = 4
                    EditorRow(
                        stringResource(R.string.button_layout_layout),
                        stringResource(profile.layout.labelRes),
                        index = 0,
                        count = rowCount,
                        enabled = !profile.isBuiltIn,
                    ) { picker = "layout" }
                    EditorRow(
                        stringResource(R.string.button_layout_m1),
                        stringResource(profile.m1.labelRes),
                        1,
                        rowCount,
                    ) { picker = "m1" }
                    EditorRow(
                        stringResource(R.string.button_layout_m2),
                        stringResource(profile.m2.labelRes),
                        2,
                        rowCount,
                    ) { picker = "m2" }
                    EditorRow(
                        stringResource(R.string.trigger_mode_title),
                        stringResource(profile.triggerMode.labelRes),
                        3,
                        rowCount,
                    ) { picker = "trigger" }
                }
            }
        }
    }

    picker?.let { active ->
        if (active == "layout") {
            ChoiceDialog(
                title = stringResource(R.string.button_layout_layout),
                choices = FaceButtonLayout.entries.map {
                    AppProfileChoice(it.sysfsValue, stringResource(it.labelRes))
                },
                selectedId = profile.layout.sysfsValue,
                showRadio = true,
                onSelected = { value ->
                    FaceButtonLayout.entries.firstOrNull { it.sysfsValue == value }
                        ?.let(onLayoutSelected)
                },
                onDismiss = { picker = null },
            )
        } else if (active == "trigger") {
            ChoiceDialog(
                title = stringResource(R.string.trigger_mode_title),
                choices = GamepadTriggerMode.entries.map {
                    AppProfileChoice(it.sysfsValue, stringResource(it.labelRes))
                },
                selectedId = profile.triggerMode.sysfsValue,
                showRadio = true,
                onSelected = { value ->
                    GamepadTriggerMode.entries.firstOrNull { it.sysfsValue == value }
                        ?.let(onTriggerModeSelected)
                },
                onDismiss = { picker = null },
            )
        } else {
            val choices = GamepadButtonMapping.entries
            ChoiceDialog(
                title = stringResource(
                    if (active == "m1") R.string.button_layout_m1 else R.string.button_layout_m2,
                ),
                choices = choices.map {
                    AppProfileChoice(it.sysfsValue, stringResource(it.labelRes))
                },
                selectedId = if (active == "m1") profile.m1.sysfsValue else profile.m2.sysfsValue,
                showRadio = true,
                onSelected = { value ->
                    GamepadButtonMapping.entries.firstOrNull { it.sysfsValue == value }?.let {
                        if (active == "m1") onM1Selected(it) else onM2Selected(it)
                    }
                },
                onDismiss = { picker = null },
            )
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename_button_layout)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.button_layout_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameDraft)
                        showRename = false
                    },
                    enabled = renameDraft.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRename = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showDelete) {
        DeleteButtonLayoutConfirmation(
            name = profile.name,
            onConfirm = onDelete,
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun EditorRow(
    title: String,
    summary: String,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = settingsSegmentedShapes(index, count),
        content = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
}

@Composable
private fun DeleteButtonLayoutConfirmation(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_button_layout)) },
        text = { Text(stringResource(R.string.delete_button_layout_confirmation, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
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
