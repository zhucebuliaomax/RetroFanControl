package com.mmax.retrocontrol.feature.authorization

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.mmax.retrocontrol.designsystem.SettingsPreferenceRow
import com.mmax.retrocontrol.designsystem.SettingsSegmentGroup
import com.mmax.retrocontrol.designsystem.SettingsSectionTitle
import com.mmax.retrocontrol.designsystem.SettingsTokens
import com.mmax.retrocontrol.designsystem.SettingsStandaloneFooterText

data class AuthorizationUiState(
    val telemetryOverlayEnabled: Boolean,
    val autoStartEnabled: Boolean,
    val profileSwitchToastsEnabled: Boolean,
    val usbThermalDisabled: Boolean,
    val thermalProtectionDisabled: Boolean,
    val rootGranted: Boolean,
    val overlayPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
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
    onUsbThermalDisabledChange: (Boolean) -> Unit,
    onThermalProtectionDisabledChange: (Boolean) -> Unit,
    onRefreshRoot: () -> Unit,
    onOpenKernelSu: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    telemetryOverlayModifier: Modifier = Modifier,
    autoStartModifier: Modifier = Modifier,
    profileSwitchToastsModifier: Modifier = Modifier,
    rootModifier: Modifier = Modifier,
    kernelSuModifier: Modifier = Modifier,
    appInfoModifier: Modifier = Modifier,
    overlayModifier: Modifier = Modifier,
    notificationsModifier: Modifier = Modifier,
    usbThermalModifier: Modifier = Modifier,
    thermalProtectionModifier: Modifier = Modifier,
) {
    SettingsSegmentGroup(modifier) {
        SettingsPreferenceRow(
            index = 0,
            count = 3,
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
            count = 2,
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
    SettingsSectionTitle(
        text = stringResource(R.string.authorization_caution),
        modifier = Modifier.padding(bottom = SettingsTokens.sectionTitleBottomPadding),
    )
    SettingsSegmentGroup {
        SettingsPreferenceRow(
            index = 0,
            count = 1,
            title = stringResource(R.string.authorization_disable_usb_thermal),
            onClick = { onUsbThermalDisabledChange(!state.usbThermalDisabled) },
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
                    checked = state.usbThermalDisabled,
                    onCheckedChange = onUsbThermalDisabledChange,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            },
        )
    }
    SettingsStandaloneFooterText(
        text = emphasizedTemperatures(
            stringResource(R.string.authorization_disable_usb_thermal_footer)
        ),
    )
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
