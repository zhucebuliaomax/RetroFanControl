@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.data.CpuFrequencyPolicy
import com.mmax.retrocontrol.data.PerformanceProfile
import com.mmax.retrocontrol.data.PerformanceProfileConfig
import com.mmax.retrocontrol.data.displayName
import com.mmax.retrocontrol.designsystem.SecondaryMenuList
import com.mmax.retrocontrol.designsystem.SecondaryMenuListItem
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PerformanceProfilesSection(
    config: PerformanceProfileConfig,
    onProfileSelected: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    profileModifier: (Int) -> Modifier = { Modifier },
) {
    val context = LocalContext.current
    if (config.policies.isEmpty()) {
        Text(
            text = stringResource(R.string.performance_policies_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    SecondaryMenuList {
        config.profiles.forEachIndexed { index, profile ->
            key(profile.id) {
                SecondaryMenuListItem(
                    index = index,
                    count = config.profiles.size,
                    onClick = { onProfileSelected(profile.id) },
                    trailingContent = if (profile.isEditable) {
                        { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                    } else {
                        null
                    },
                    supportingContent = { Text(profile.frequencySummary(config.policies)) },
                    modifier = profileModifier(index).bringIntoViewOnFocus(),
                    content = {
                        Text(
                            text = profile.displayName(context),
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
fun AddPerformanceProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.add_performance_profile)) },
    )
}

@Composable
fun PerformanceProfileEditorDialog(
    profile: PerformanceProfile,
    policies: List<CpuFrequencyPolicy>,
    onSave: (String, Map<Int, Int>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(profile.id) {
        mutableStateOf(profile.customName ?: profile.displayName(context))
    }
    var values by remember(profile.id, profile.maxFrequencies) {
        mutableStateOf(profile.maxFrequencies)
    }
    var showRename by remember(profile.id) { mutableStateOf(false) }
    var renameDraft by remember(profile.id, name) { mutableStateOf(name) }
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
                    if (profile.isEditable) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                renameDraft = name
                                showRename = true
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_edit_square),
                                contentDescription = stringResource(R.string.rename_performance_profile),
                            )
                        }
                        IconButton(
                            onClick = { showDelete = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = stringResource(R.string.delete_performance_profile),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            text = profile.displayName(context),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    policies.forEach { policy ->
                        FrequencyPolicyEditor(
                            policy = policy,
                            frequency = values[policy.id] ?: policy.currentMaxFrequency,
                            enabled = profile.isEditable,
                            onFrequencyChanged = { frequency ->
                                values = values + (policy.id to frequency)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.cancel))
                    }
                    if (profile.isEditable) {
                        TextButton(
                            onClick = { onSave(name, values) },
                            enabled = name.isNotBlank(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename_performance_profile)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.performance_profile_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        name = renameDraft.trim()
                        showRename = false
                    },
                    enabled = renameDraft.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.save)) }
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
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_performance_profile)) },
            text = {
                Text(stringResource(R.string.delete_performance_profile_confirmation, name))
            },
            confirmButton = {
                TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDelete = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FrequencyPolicyEditor(
    policy: CpuFrequencyPolicy,
    frequency: Int,
    enabled: Boolean,
    onFrequencyChanged: (Int) -> Unit,
) {
    val frequencies = policy.supportedFrequencies
    val selectedIndex = frequencies.indices.minByOrNull { index ->
        abs(frequencies[index].toLong() - frequency.toLong())
    } ?: 0
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = frequencyPolicyName(policy.cpuIds),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = stringResource(R.string.performance_policy_number, policy.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatFrequency(frequency),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (frequencies.size > 1) {
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { index ->
                    onFrequencyChanged(frequencies[index.roundToInt().coerceIn(frequencies.indices)])
                },
                valueRange = 0f..frequencies.lastIndex.toFloat(),
                steps = (frequencies.size - 2).coerceAtLeast(0),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun frequencyPolicyName(cpuIds: List<Int>): String = when (cpuIds.toSet()) {
    setOf(0, 1, 2) -> stringResource(R.string.efficiency_cores)
    setOf(3, 4, 5, 6) -> stringResource(R.string.performance_cores)
    setOf(7) -> stringResource(R.string.prime_core)
    else -> formatCpuRange(cpuIds)
}

internal fun formatFrequency(frequencyKhz: Int): String = when {
    frequencyKhz >= 1_000_000 -> String.format(
        Locale.getDefault(),
        "%.2f GHz",
        frequencyKhz / 1_000_000.0,
    )
    else -> String.format(Locale.getDefault(), "%.0f MHz", frequencyKhz / 1_000.0)
}

internal fun formatCpuRange(cpuIds: List<Int>): String {
    if (cpuIds.isEmpty()) return "CPU"
    val sorted = cpuIds.distinct().sorted()
    return if (sorted.size > 1 && sorted.zipWithNext().all { (a, b) -> b == a + 1 }) {
        "CPU ${sorted.first()}–${sorted.last()}"
    } else {
        "CPU ${sorted.joinToString(", ")}"
    }
}

private fun PerformanceProfile.frequencySummary(
    policies: List<CpuFrequencyPolicy>,
): String = policies.joinToString(" · ") { policy ->
    formatFrequency(maxFrequencies[policy.id] ?: policy.currentMaxFrequency)
}
