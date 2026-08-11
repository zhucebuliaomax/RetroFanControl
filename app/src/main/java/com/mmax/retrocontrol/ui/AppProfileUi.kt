@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.data.AppControlProfile
import com.mmax.retrocontrol.designsystem.SettingsListDialog
import com.mmax.retrocontrol.designsystem.settingsSegmentedShapes
import com.mmax.retrocontrol.designsystem.SettingsSectionTitle
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus

data class AppProfileChoice(val id: String?, val name: String)

private enum class DefaultPicker { GAME, NON_GAME }
private enum class AppPicker { PROFILE, FAN, JOYSTICK, BUTTON, PERFORMANCE }

@Composable
fun DefaultProfileSection(
    gameProfileId: String,
    gameProfileName: String,
    nonGameProfileId: String,
    nonGameProfileName: String,
    profileChoices: List<AppProfileChoice>,
    onDefaultProfileSelected: (Boolean, String) -> Unit,
    onProfileEdit: (String) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<DefaultPicker?>(null) }
    Column(modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.app_default_preset))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingRow(
                title = stringResource(R.string.game_profile),
                summary = gameProfileName,
                index = 0,
                count = 2,
                onClick = { picker = DefaultPicker.GAME },
            )
            SettingRow(
                title = stringResource(R.string.non_game_profile),
                summary = nonGameProfileName,
                index = 1,
                count = 2,
                onClick = { picker = DefaultPicker.NON_GAME },
            )
        }
    }

    picker?.let { active ->
        when (active) {
            DefaultPicker.GAME, DefaultPicker.NON_GAME -> ChoiceDialog(
                title = stringResource(R.string.select_preset),
                choices = profileChoices,
                selectedId = if (active == DefaultPicker.GAME) gameProfileId else nonGameProfileId,
                disabledIds = setOf(
                    if (active == DefaultPicker.GAME) nonGameProfileId else gameProfileId
                ),
                showRadio = true,
                addLabel = stringResource(R.string.add_preset),
                onSelected = { id -> id?.let { onDefaultProfileSelected(active == DefaultPicker.GAME, it) } },
                onItemClick = { id -> id?.let(onProfileEdit) },
                onAdd = onAddProfile,
                onDismiss = { picker = null },
            )
        }
    }
}

@Composable
fun AppProfileSection(
    profile: AppControlProfile?,
    selectedProfileName: String,
    selectedFanCurveName: String,
    selectedJoystickProfileName: String,
    selectedButtonLayoutName: String,
    selectedPerformanceProfileName: String,
    profileChoices: List<AppProfileChoice>,
    fanCurveChoices: List<AppProfileChoice>,
    joystickChoices: List<AppProfileChoice>,
    buttonLayoutChoices: List<AppProfileChoice>,
    performanceChoices: List<AppProfileChoice>,
    onProfileSelected: (String?) -> Unit,
    onFanCurveSelected: (String?) -> Unit,
    onJoystickSelected: (String?) -> Unit,
    onButtonLayoutSelected: (String?) -> Unit,
    onPerformanceSelected: (String?) -> Unit,
    onProfileEdit: (String) -> Unit,
    onAddProfile: () -> Unit,
    onFanCurveEdit: (String) -> Unit,
    onAddFanCurve: () -> Unit,
    onJoystickEdit: (String) -> Unit,
    onAddJoystick: () -> Unit,
    onButtonLayoutEdit: (String) -> Unit,
    onAddButtonLayout: () -> Unit,
    onPerformanceEdit: (String) -> Unit,
    onAddPerformance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember(profile?.packageName) { mutableStateOf<AppPicker?>(null) }
    Column(modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.app_profile))
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = stringResource(R.string.app_profile_preset),
            summary = selectedProfileName,
            index = 0,
            count = 1,
            onClick = { picker = AppPicker.PROFILE },
        )
        Spacer(Modifier.height(24.dp))
        SettingsSectionTitle(stringResource(R.string.control_title))
        Spacer(Modifier.height(8.dp))
        ControlsCard(
            fanSummary = selectedFanCurveName,
            joystickSummary = selectedJoystickProfileName,
            buttonSummary = selectedButtonLayoutName,
            performanceSummary = selectedPerformanceProfileName,
            onFanClick = { picker = AppPicker.FAN },
            onJoystickClick = { picker = AppPicker.JOYSTICK },
            onButtonClick = { picker = AppPicker.BUTTON },
            onPerformanceClick = { picker = AppPicker.PERFORMANCE },
        )
    }

    picker?.let { active ->
        when (active) {
            AppPicker.PROFILE -> ChoiceDialog(
                title = stringResource(R.string.select_preset),
                choices = profileChoices,
                selectedId = profile?.presetId,
                showRadio = true,
                addLabel = stringResource(R.string.add_preset),
                onSelected = onProfileSelected,
                onItemClick = { id -> id?.let(onProfileEdit) },
                onAdd = onAddProfile,
                onDismiss = { picker = null },
            )
            AppPicker.FAN -> ChoiceDialog(
                title = stringResource(R.string.select_fan_curve),
                choices = fanCurveChoices,
                selectedId = profile?.fanCurveId,
                showRadio = true,
                addLabel = stringResource(R.string.add_fan_curve),
                onSelected = onFanCurveSelected,
                onItemClick = { id -> id?.let(onFanCurveEdit) },
                onAdd = onAddFanCurve,
                onDismiss = { picker = null },
            )
            AppPicker.JOYSTICK -> ChoiceDialog(
                title = stringResource(R.string.select_joystick_profile),
                choices = joystickChoices,
                selectedId = profile?.joystickId,
                showRadio = true,
                addLabel = stringResource(R.string.add_preset),
                onSelected = onJoystickSelected,
                onItemClick = { id -> id?.let(onJoystickEdit) },
                onAdd = onAddJoystick,
                onDismiss = { picker = null },
            )
            AppPicker.PERFORMANCE -> ChoiceDialog(
                title = stringResource(R.string.control_core),
                choices = performanceChoices,
                selectedId = profile?.performanceProfileId,
                showRadio = true,
                addLabel = stringResource(R.string.add_performance_profile),
                onSelected = onPerformanceSelected,
                onItemClick = { id -> id?.let(onPerformanceEdit) },
                onAdd = onAddPerformance,
                onDismiss = { picker = null },
            )
            AppPicker.BUTTON -> ChoiceDialog(
                title = stringResource(R.string.control_button_layout),
                choices = buttonLayoutChoices,
                selectedId = profile?.buttonLayoutId,
                showRadio = true,
                addLabel = stringResource(R.string.add_button_layout),
                onSelected = onButtonLayoutSelected,
                onItemClick = { id -> id?.let(onButtonLayoutEdit) },
                onAdd = onAddButtonLayout,
                onDismiss = { picker = null },
            )
        }
    }
}

@Composable
private fun ControlsCard(
    fanSummary: String?,
    joystickSummary: String?,
    buttonSummary: String?,
    performanceSummary: String?,
    onFanClick: () -> Unit,
    onJoystickClick: () -> Unit,
    onButtonClick: () -> Unit,
    onPerformanceClick: () -> Unit,
) {
    val rows = listOf(
        Triple(R.string.control_fan, fanSummary, onFanClick),
        Triple(R.string.control_joystick, joystickSummary, onJoystickClick),
        Triple(R.string.control_button_layout, buttonSummary, onButtonClick),
        Triple(R.string.control_core, performanceSummary, onPerformanceClick),
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, (title, summary, action) ->
            SettingRow(stringResource(title), summary, index, rows.size, action)
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = settingsSegmentedShapes(
            index = index,
            count = count,
        ),
        content = { Text(title) },
        supportingContent = summary?.let { value -> { Text(value) } },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
}

@Composable
fun ChoiceDialog(
    title: String,
    choices: List<AppProfileChoice>,
    selectedId: String? = null,
    disabledIds: Set<String?> = emptySet(),
    showRadio: Boolean,
    addLabel: String? = null,
    onSelected: (String?) -> Unit = {},
    onItemClick: (String?) -> Unit = {},
    onAdd: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    SettingsListDialog(
        title = title,
        itemCount = choices.size,
        itemLabel = { index -> choices[index].name },
        selectedIndex = choices.indexOfFirst { it.id == selectedId },
        showRadio = showRadio,
        itemEnabled = { index -> choices[index].id !in disabledIds },
        addLabel = addLabel,
        onSelected = { index -> onSelected(choices[index].id) },
        onItemClick = { index ->
            val id = choices[index].id
            if (showRadio) onSelected(id)
            onItemClick(id)
        },
        onAdd = onAdd,
        emptyLabel = stringResource(R.string.no_options_available),
        cancelLabel = stringResource(R.string.cancel),
        onDismiss = onDismiss,
    )
}
