package com.mmax.retrocontrol.feature.authorization

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.mmax.retrocontrol.designsystem.SettingsPreferenceRow
import com.mmax.retrocontrol.designsystem.SettingsListDialog
import com.mmax.retrocontrol.designsystem.SettingsSegmentGroup
import com.mmax.retrocontrol.designsystem.SettingsSectionTitle
import com.mmax.retrocontrol.designsystem.SettingsTokens
import com.mmax.retrocontrol.designsystem.SettingsStandaloneFooterText
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus
import com.mmax.retrocontrol.designsystem.settingsSegmentedShapes
import kotlin.math.roundToInt

private const val CHARGING_THRESHOLD_MIN = 50
private const val CHARGING_THRESHOLD_MAX = 100
private const val CHARGING_THRESHOLD_STEP = 5
private const val CHARGING_THRESHOLD_STEP_COUNT =
    (CHARGING_THRESHOLD_MAX - CHARGING_THRESHOLD_MIN) / CHARGING_THRESHOLD_STEP - 1

data class AuthorizationUiState(
    val telemetryOverlayEnabled: Boolean,
    val autoStartEnabled: Boolean,
    val profileSwitchToastsEnabled: Boolean,
    val usbThermalControlEnabled: Boolean,
    val thermalProtectionDisabled: Boolean,
    val rootGranted: Boolean,
    val overlayPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val ambilightLeftStickLower: Boolean,
    val preserveBypassCharging: Boolean,
    val chargingThreshold: Int,
)

/**
 * A host-independent authorization card. The host supplies platform actions,
 * which keeps KernelSU, app-info and notification routing reusable.
 */
@Composable
fun AuthorizationManagementSection(
    state: AuthorizationUiState,
    onTelemetryOverlayClick: () -> Unit,
    onTelemetryOverlayEnabledChange: (Boolean) -> Unit,
    onAutoStartEnabledChange: (Boolean) -> Unit,
    onProfileSwitchToastsEnabledChange: (Boolean) -> Unit,
    onUsbThermalControlEnabledChange: (Boolean) -> Unit,
    onUsbThermalFanCurveClick: () -> Unit,
    onThermalProtectionDisabledChange: (Boolean) -> Unit,
    onRefreshRoot: () -> Unit,
    onOpenKernelSu: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAmbilightLeftStickLowerChange: (Boolean) -> Unit,
    onPreserveBypassChargingChange: (Boolean) -> Unit,
    onChargingThresholdChange: (Int) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier,
    telemetryOverlayModifier: Modifier = Modifier,
    autoStartModifier: Modifier = Modifier,
    profileSwitchToastsModifier: Modifier = Modifier,
    rootModifier: Modifier = Modifier,
    kernelSuModifier: Modifier = Modifier,
    appInfoModifier: Modifier = Modifier,
    overlayModifier: Modifier = Modifier,
    notificationsModifier: Modifier = Modifier,
    preserveBypassChargingModifier: Modifier = Modifier,
    chargingThresholdModifier: Modifier = Modifier,
    ambilightModifier: Modifier = Modifier,
    usbThermalModifier: Modifier = Modifier,
    usbThermalFanCurveModifier: Modifier = Modifier,
    thermalProtectionModifier: Modifier = Modifier,
    exportDataModifier: Modifier = Modifier,
    importDataModifier: Modifier = Modifier,
    resetDataModifier: Modifier = Modifier,
) {
    var showAmbilightLayoutDialog by remember { mutableStateOf(false) }
    var chargingThreshold by remember { mutableIntStateOf(state.chargingThreshold) }
    LaunchedEffect(state.chargingThreshold) {
        chargingThreshold = state.chargingThreshold
    }
    SettingsSegmentGroup(modifier) {
        SettingsPreferenceRow(
            index = 0,
            count = 2,
            title = stringResource(R.string.authorization_auto_start),
            onClick = { onAutoStartEnabledChange(!state.autoStartEnabled) },
            modifier = autoStartModifier,
            trailingContent = {
                Switch(
                    checked = state.autoStartEnabled,
                    onCheckedChange = onAutoStartEnabledChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            },
        )
        SettingsPreferenceRow(
            index = 1,
            count = 3,
            title = stringResource(R.string.authorization_root),
            summary = stringResource(
                if (state.rootGranted) {
                    R.string.authorization_root_granted
                } else {
                    R.string.authorization_root_not_granted
                }
            ),
            onClick = onRefreshRoot,
            modifier = rootModifier,
            trailingIcon = if (state.rootGranted) {
                Icons.Default.Check
            } else {
                Icons.Default.Refresh
            },
        )
        SettingsPreferenceRow(
            index = 2,
            count = 3,
            title = stringResource(R.string.authorization_kernelsu),
            summary = stringResource(R.string.authorization_kernelsu_summary),
            onClick = onOpenKernelSu,
            modifier = kernelSuModifier,
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )
    }
    Spacer(Modifier.height(20.dp))
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 2,
            title = stringResource(R.string.authorization_preserve_bypass_charging),
            summary = stringResource(R.string.authorization_preserve_bypass_charging_summary),
            onClick = {
                onPreserveBypassChargingChange(!state.preserveBypassCharging)
            },
            modifier = preserveBypassChargingModifier,
            trailingContent = {
                Switch(
                    checked = state.preserveBypassCharging,
                    onCheckedChange = onPreserveBypassChargingChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = settingsSegmentedShapes(index = 1, count = 2).shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.authorization_charging_threshold),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.authorization_charging_threshold_value,
                            chargingThreshold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = chargingThreshold.toFloat(),
                    onValueChange = { value ->
                        chargingThreshold = (value / CHARGING_THRESHOLD_STEP).roundToInt()
                            .times(CHARGING_THRESHOLD_STEP)
                            .coerceIn(CHARGING_THRESHOLD_MIN, CHARGING_THRESHOLD_MAX)
                    },
                    onValueChangeFinished = {
                        onChargingThresholdChange(chargingThreshold)
                    },
                    valueRange = CHARGING_THRESHOLD_MIN.toFloat()..
                        CHARGING_THRESHOLD_MAX.toFloat(),
                    steps = CHARGING_THRESHOLD_STEP_COUNT,
                    modifier = chargingThresholdModifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus(),
                )
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 3,
            title = stringResource(R.string.authorization_telemetry_overlay),
            summary = stringResource(
                if (state.overlayPermissionGranted) {
                    R.string.authorization_telemetry_overlay_summary
                } else {
                    R.string.authorization_telemetry_overlay_permission_missing
                }
            ),
            onClick = onTelemetryOverlayClick,
            modifier = telemetryOverlayModifier,
            trailingContent = {
                Switch(
                    checked = state.telemetryOverlayEnabled && state.overlayPermissionGranted,
                    onCheckedChange = onTelemetryOverlayEnabledChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            },
        )
        SettingsPreferenceRow(
            index = 1,
            count = 3,
            title = stringResource(R.string.authorization_overlay),
            summary = stringResource(
                if (state.overlayPermissionGranted) {
                    R.string.authorization_overlay_granted
                } else {
                    R.string.authorization_overlay_not_granted
                }
            ),
            onClick = onOpenOverlaySettings,
            modifier = overlayModifier,
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )
        SettingsPreferenceRow(
            index = 2,
            count = 3,
            title = stringResource(R.string.authorization_app_info),
            summary = stringResource(R.string.authorization_app_info_summary),
            onClick = onOpenAppInfo,
            modifier = appInfoModifier,
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )
    }
    Spacer(Modifier.height(20.dp))
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 3,
            title = stringResource(R.string.authorization_profile_switch_toasts),
            summary = stringResource(
                R.string.authorization_profile_switch_toasts_summary
            ),
            onClick = {
                onProfileSwitchToastsEnabledChange(
                    !state.profileSwitchToastsEnabled
                )
            },
            modifier = profileSwitchToastsModifier,
            trailingContent = {
                Switch(
                    checked = state.profileSwitchToastsEnabled,
                    onCheckedChange = onProfileSwitchToastsEnabledChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            },
        )
        SettingsPreferenceRow(
            index = 1,
            count = 2,
            title = stringResource(R.string.authorization_notifications),
            summary = stringResource(
                if (state.notificationsEnabled) {
                    R.string.authorization_notifications_enabled
                } else {
                    R.string.authorization_notifications_disabled
                }
            ),
            onClick = onOpenNotificationSettings,
            modifier = notificationsModifier,
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        )
    }
    Spacer(Modifier.height(20.dp))
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 1,
            title = stringResource(R.string.authorization_ambilight_left_stick_layout),
            summary = stringResource(
                if (state.ambilightLeftStickLower) {
                    R.string.authorization_ambilight_left_stick_lower
                } else {
                    R.string.authorization_ambilight_left_stick_upper
                }
            ),
            onClick = { showAmbilightLayoutDialog = true },
            modifier = ambilightModifier,
            trailingIcon = Icons.Default.ChevronRight,
        )
    }
    if (showAmbilightLayoutDialog) {
        val ambilightLayoutLabels = listOf(
            stringResource(R.string.authorization_ambilight_left_stick_upper),
            stringResource(R.string.authorization_ambilight_left_stick_lower),
        )
        SettingsListDialog(
            title = stringResource(R.string.authorization_ambilight_left_stick_layout),
            itemCount = 2,
            itemLabel = ambilightLayoutLabels::get,
            onItemClick = { index ->
                onAmbilightLeftStickLowerChange(index == 1)
                showAmbilightLayoutDialog = false
            },
            onDismiss = { showAmbilightLayoutDialog = false },
            selectedIndex = if (state.ambilightLeftStickLower) 1 else 0,
            showRadio = true,
            cancelLabel = stringResource(R.string.authorization_cancel),
        )
    }
    Spacer(Modifier.height(20.dp))
    SettingsSectionTitle(
        text = stringResource(R.string.authorization_caution),
        modifier = Modifier.padding(bottom = SettingsTokens.sectionTitleBottomPadding),
    )
    SettingsSegmentGroup {
        val itemCount = if (state.usbThermalControlEnabled) 2 else 1
        SettingsPreferenceRow(
            index = 0,
            count = itemCount,
            title = stringResource(R.string.authorization_usb_thermal_control),
            onClick = {
                onUsbThermalControlEnabledChange(!state.usbThermalControlEnabled)
            },
            modifier = usbThermalModifier,
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_caution),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Switch(
                    checked = state.usbThermalControlEnabled,
                    onCheckedChange = onUsbThermalControlEnabledChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            },
        )
        if (state.usbThermalControlEnabled) {
            SettingsPreferenceRow(
                index = 1,
                count = itemCount,
                title = stringResource(R.string.authorization_usb_thermal_fan_curve),
                onClick = onUsbThermalFanCurveClick,
                modifier = usbThermalFanCurveModifier,
                trailingIcon = Icons.Default.ChevronRight,
            )
        }
    }
    UsbThermalFooter()
    Spacer(Modifier.height(20.dp))
    SettingsSectionTitle(
        text = stringResource(R.string.authorization_data),
        modifier = Modifier.padding(bottom = SettingsTokens.sectionTitleBottomPadding),
    )
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 3,
            title = stringResource(R.string.authorization_export_data),
            onClick = onExportData,
            modifier = exportDataModifier,
            trailingIcon = Icons.Default.FileUpload,
        )
        SettingsPreferenceRow(
            index = 1,
            count = 3,
            title = stringResource(R.string.authorization_import_data),
            summary = stringResource(R.string.authorization_import_data_summary),
            onClick = onImportData,
            modifier = importDataModifier,
            trailingIcon = Icons.Default.FileDownload,
        )
        SettingsPreferenceRow(
            index = 2,
            count = 3,
            title = stringResource(R.string.authorization_reset_data),
            onClick = onResetData,
            modifier = resetDataModifier,
            trailingIcon = Icons.Default.DeleteForever,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Spacer(Modifier.height(20.dp))
    val dangerContentColor = MaterialTheme.colorScheme.onErrorContainer
    SettingsSectionTitle(
        text = stringResource(R.string.authorization_dangerous),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(bottom = SettingsTokens.sectionTitleBottomPadding),
    )
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 1,
            title = stringResource(R.string.authorization_disable_thermal_protection),
            onClick = {
                onThermalProtectionDisabledChange(!state.thermalProtectionDisabled)
            },
            modifier = thermalProtectionModifier,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = dangerContentColor,
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_skull),
                    contentDescription = null,
                    tint = dangerContentColor,
                )
            },
            trailingContent = {
                Switch(
                    checked = state.thermalProtectionDisabled,
                    onCheckedChange = onThermalProtectionDisabledChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                    colors = dangerSwitchColors(),
                )
            },
        )
    }
    SettingsStandaloneFooterText(
        text = AnnotatedString(
            stringResource(R.string.authorization_disable_thermal_protection_footer)
        ),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun UsbThermalFooter() {
    val strategyUrl = stringResource(R.string.authorization_usb_thermal_strategy_url)
    val strategy = stringResource(R.string.authorization_usb_thermal_strategy)
    val footer = stringResource(R.string.authorization_usb_thermal_footer)
    val iconId = "open-in-new"
    SettingsStandaloneFooterText(
        text = linkedThermalFooter(footer, strategy, strategyUrl, iconId),
        inlineContent = mapOf(
            iconId to InlineTextContent(
                Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.Center),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(
                        R.string.authorization_open_usb_thermal_strategy
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        ),
    )
}

private fun linkedThermalFooter(
    text: String,
    strategy: String,
    url: String,
    iconId: String,
) = buildAnnotatedString {
    val start = text.indexOf(strategy)
    if (start < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, start))
    withLink(LinkAnnotation.Url(url)) {
        withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Unspecified)) {
            append(strategy.replace(' ', '\u00A0'))
            appendInlineContent(iconId, "↗")
        }
    }
    append(text.substring(start + strategy.length))
    listOf("43°C", "45°C").forEach { temperature ->
        val temperatureStart = text.indexOf(temperature)
        if (temperatureStart >= 0) {
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                temperatureStart,
                temperatureStart + temperature.length,
            )
        }
    }
}

private fun emphasizedTemperatures(text: String) = buildAnnotatedString {
    append(text)
    listOf("43°C", "45°C").forEach { temperature ->
        var start = text.indexOf(temperature)
        while (start >= 0) {
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.Bold),
                start = start,
                end = start + temperature.length,
            )
            start = text.indexOf(temperature, start + temperature.length)
        }
    }
}

@Composable
private fun dangerSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onError,
    checkedTrackColor = MaterialTheme.colorScheme.error,
    checkedBorderColor = MaterialTheme.colorScheme.error,
    uncheckedThumbColor = MaterialTheme.colorScheme.onErrorContainer,
    uncheckedTrackColor = MaterialTheme.colorScheme.errorContainer,
    uncheckedBorderColor = MaterialTheme.colorScheme.onErrorContainer,
)
