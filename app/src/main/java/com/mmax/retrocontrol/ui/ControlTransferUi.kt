@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.designsystem.SecondaryMenuList
import com.mmax.retrocontrol.designsystem.SecondaryMenuSelectableListItem

data class ExportChoice(
    val id: String,
    val name: String,
)

@Composable
fun ControlTransferFabMenu(
    addLabel: String,
    onAdd: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    collapsedUpFocusRequester: FocusRequester = FocusRequester.Default,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fabFocusRequester = remember { FocusRequester() }
    val importFocusRequester = remember { FocusRequester() }
    val exportFocusRequester = remember { FocusRequester() }
    val addFocusRequester = remember { FocusRequester() }
    BackHandler(enabled = expanded) { expanded = false }
    val menuContentDescription = stringResource(R.string.more_actions)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = modifier
                    .focusRequester(fabFocusRequester)
                    .focusProperties {
                        up = if (expanded) addFocusRequester else collapsedUpFocusRequester
                        down = if (expanded) {
                            when {
                                onImport != null -> importFocusRequester
                                onExport != null -> exportFocusRequester
                                else -> addFocusRequester
                            }
                        } else {
                            FocusRequester.Cancel
                        }
                    },
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = menuContentDescription,
                    modifier = Modifier.animateIcon(checkedProgress = { checkedProgress }),
                )
            }
        },
    ) {
        onImport?.let { importItems ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    importItems()
                },
                text = { Text(stringResource(R.string.import_items)) },
                icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                modifier = Modifier
                    .focusRequester(importFocusRequester)
                    .focusProperties {
                        up = fabFocusRequester
                        down = if (onExport != null) exportFocusRequester else addFocusRequester
                    },
            )
        }
        onExport?.let { exportItems ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    exportItems()
                },
                text = { Text(stringResource(R.string.export_items)) },
                icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                modifier = Modifier
                    .focusRequester(exportFocusRequester)
                    .focusProperties {
                        up = if (onImport != null) importFocusRequester else fabFocusRequester
                        down = addFocusRequester
                    },
            )
        }
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAdd()
            },
            text = { Text(addLabel) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier
                .focusRequester(addFocusRequester)
                .focusProperties {
                    up = when {
                        onExport != null -> exportFocusRequester
                        onImport != null -> importFocusRequester
                        else -> fabFocusRequester
                    }
                    down = fabFocusRequester
                },
        )
    }
}

@Composable
fun ExportSelectionDialog(
    choices: List<ExportChoice>,
    onExport: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(choices) { mutableStateListOf<String>() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.select_items_to_export),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.size(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (choices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_items_to_export),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SecondaryMenuList {
                            choices.forEachIndexed { index, choice ->
                                val isSelected = choice.id in selected
                                SecondaryMenuSelectableListItem(
                                    selected = isSelected,
                                    index = index,
                                    count = choices.size,
                                    onClick = {
                                        if (isSelected) selected.remove(choice.id)
                                        else selected.add(choice.id)
                                    },
                                    content = { Text(choice.name) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { onExport(selected.toSet()) },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.export_items))
                    }
                }
            }
        }
    }
}
