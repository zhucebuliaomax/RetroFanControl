@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mmax.retrocontrol.feature.fan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmax.retrocontrol.designsystem.SecondaryMenuList
import com.mmax.retrocontrol.designsystem.SecondaryMenuListItem
import com.mmax.retrocontrol.designsystem.SettingsSectionTitle
import com.mmax.retrocontrol.designsystem.SettingsTokens
import com.mmax.retrocontrol.designsystem.TransferContextMenuContainer

data class FanProfileSectionState(
    val profiles: List<FanProfileItemUiState>,
)

data class FanProfileItemUiState(
    val id: String,
    val name: String,
    val controlPointCount: Int,
)

/**
 * Host-independent fan profile manager. Persistence and curve editing remain
 * host responsibilities, allowing the list to be embedded as a feature subset.
 */
@Composable
fun FanProfilesSection(
    state: FanProfileSectionState,
    onProfileSelected: (String) -> Unit,
    importLabel: String,
    exportLabel: String,
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    showTitle: Boolean = true,
    modifier: Modifier = Modifier,
    offModifier: Modifier = Modifier,
    profileModifier: (Int) -> Modifier = { Modifier },
) {
    Column(modifier = modifier) {
        if (showTitle) {
            SettingsSectionTitle(
                text = stringResource(R.string.fanfeature_profiles),
                modifier = Modifier.padding(bottom = SettingsTokens.sectionTitleBottomPadding),
            )
        }

        val itemCount = state.profiles.size + 1
        SecondaryMenuList {
            FanProfileListItem(
                name = stringResource(R.string.fanfeature_off_title),
                summary = stringResource(R.string.fanfeature_off),
                index = 0,
                count = itemCount,
                onClick = {},
                modifier = offModifier,
            )
            state.profiles.forEachIndexed { index, profile ->
                TransferContextMenuContainer(
                    importLabel = importLabel,
                    exportLabel = exportLabel,
                    onImport = onImport,
                    onExport = { onExport(profile.id) },
                ) { onLongClick ->
                    FanProfileListItem(
                        name = profile.name,
                        summary = stringResource(
                            R.string.fanfeature_control_points,
                            profile.controlPointCount,
                        ),
                        index = index + 1,
                        count = itemCount,
                        onClick = { onProfileSelected(profile.id) },
                        onLongClick = onLongClick,
                        showChevron = true,
                        modifier = profileModifier(index),
                    )
                }
            }
        }
    }
}

@Composable
fun FanProfilesAddCurveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
            )
        },
        text = { Text(stringResource(R.string.fanfeature_add_curve)) },
    )
}

@Composable
private fun FanProfileListItem(
    name: String,
    summary: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    modifier: Modifier = Modifier,
) {
    SecondaryMenuListItem(
        index = index,
        count = count,
        onClick = onClick,
        onLongClick = onLongClick,
        content = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (showChevron) {
            { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        } else {
            null
        },
        modifier = modifier,
    )
}
