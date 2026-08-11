@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.mmax.retrocontrol.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.designsystem.FocusScrollMargin
import com.mmax.retrocontrol.designsystem.bringIntoViewOnFocus
import com.mmax.retrocontrol.designsystem.settingsSegmentedShapes

private enum class AppListFilter {
    GAME,
    OTHER,
    ALL,
}

internal enum class DashboardDestination(
    @param:StringRes val label: Int,
    @param:DrawableRes val outlinedIcon: Int,
    @param:DrawableRes val filledIcon: Int,
) {
    CONTROLS(
        R.string.nav_controls,
        R.drawable.nav_controls_outlined,
        R.drawable.nav_controls_filled,
    ),
    APPS(
        R.string.nav_apps,
        R.drawable.nav_apps_outlined,
        R.drawable.nav_apps_filled,
    ),
    ACCESS(
        R.string.nav_access,
        R.drawable.nav_access_outlined,
        R.drawable.nav_access_filled,
    ),
}

internal enum class ControlModule(@param:StringRes val label: Int) {
    PRESET(R.string.control_preset),
    FAN(R.string.control_fan),
    JOYSTICK(R.string.control_joystick),
    BUTTON_LAYOUT(R.string.control_button_layout),
    CORE(R.string.control_core),
}

/**
 * Landscape-first Material layout. Window size classes keep the list/detail
 * behavior usable when the same activity is resized or shown in multi-window.
 */
@Composable
internal fun AdaptiveDashboardScaffold(
    selectedDestination: DashboardDestination,
    onDestinationSelected: (DashboardDestination) -> Unit,
    selectedControl: ControlModule?,
    onControlSelected: (ControlModule?) -> Unit,
    selectedApp: String?,
    onAppSelected: (String?) -> Unit,
    installedApps: List<InstalledAppInfo>,
    navigationFocusRequesters: List<FocusRequester>,
    controlFocusRequesters: List<FocusRequester>,
    appFocusRequester: FocusRequester,
    emptyDetailFocusRequester: FocusRequester,
    fanContent: @Composable () -> Unit,
    fanAction: @Composable () -> Unit,
    joystickContent: @Composable () -> Unit,
    joystickAction: @Composable () -> Unit,
    buttonLayoutContent: @Composable () -> Unit,
    buttonLayoutAction: @Composable () -> Unit,
    presetContent: @Composable () -> Unit,
    presetAction: @Composable () -> Unit,
    performanceContent: @Composable () -> Unit,
    performanceAction: @Composable () -> Unit,
    appProfileContent: @Composable (String) -> Unit,
    accessContent: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val showTwoControlPanes = windowSizeClass.isWidthAtLeastBreakpoint(
        WIDTH_DP_EXPANDED_LOWER_BOUND
    )
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)
    val isNavigationBar = navigationSuiteType == NavigationSuiteType.NavigationBar ||
        navigationSuiteType == NavigationSuiteType.ShortNavigationBarCompact ||
        navigationSuiteType == NavigationSuiteType.ShortNavigationBarMedium

    NavigationSuiteScaffold(
        navigationItems = {
            DashboardDestination.entries.forEachIndexed { index, destination ->
                val selected = destination == selectedDestination
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { onDestinationSelected(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(
                                if (selected) destination.filledIcon else destination.outlinedIcon
                            ),
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .focusRequester(navigationFocusRequesters[index])
                        .focusProperties {
                            if (isNavigationBar) {
                                left = if (index == 0) {
                                    FocusRequester.Default
                                } else {
                                    navigationFocusRequesters[index - 1]
                                }
                                right = if (index == DashboardDestination.entries.lastIndex) {
                                    FocusRequester.Default
                                } else {
                                    navigationFocusRequesters[index + 1]
                                }
                            } else {
                                up = if (index == 0) {
                                    FocusRequester.Default
                                } else {
                                    navigationFocusRequesters[index - 1]
                                }
                                down = if (index == DashboardDestination.entries.lastIndex) {
                                    FocusRequester.Default
                                } else {
                                    navigationFocusRequesters[index + 1]
                                }
                            }
                        },
                    navigationSuiteType = navigationSuiteType,
                )
            }
        },
        navigationSuiteType = navigationSuiteType,
        navigationItemVerticalArrangement = Arrangement.Center,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            when (selectedDestination) {
                DashboardDestination.CONTROLS -> ControlsPage(
                    selectedControl = selectedControl,
                    onControlSelected = onControlSelected,
                    showTwoPanes = showTwoControlPanes,
                    controlFocusRequesters = controlFocusRequesters,
                    emptyDetailFocusRequester = emptyDetailFocusRequester,
                    fanContent = fanContent,
                    fanAction = fanAction,
                    joystickContent = joystickContent,
                    joystickAction = joystickAction,
                    buttonLayoutContent = buttonLayoutContent,
                    buttonLayoutAction = buttonLayoutAction,
                    presetContent = presetContent,
                    presetAction = presetAction,
                    performanceContent = performanceContent,
                    performanceAction = performanceAction,
                )

                DashboardDestination.APPS -> AppsPage(
                    selectedApp = selectedApp,
                    onAppSelected = onAppSelected,
                    installedApps = installedApps,
                    showTwoPanes = showTwoControlPanes,
                    appFocusRequester = appFocusRequester,
                    appProfileContent = appProfileContent,
                )

                DashboardDestination.ACCESS -> DashboardPage(
                    title = stringResource(R.string.nav_access),
                ) {
                    accessContent()
                    Spacer(Modifier.size(32.dp))
                    footer()
                }
            }
        }
    }
}

@Composable
private fun DashboardPage(
    title: String,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FocusScrollMargin {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            CollapsingTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                horizontalPadding = 30.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 30.dp),
            ) {
                Spacer(Modifier.size(10.dp))
                Box(Modifier.widthIn(max = 720.dp)) {
                    Column { content() }
                }
                Spacer(Modifier.size(30.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun ControlsPage(
    selectedControl: ControlModule?,
    onControlSelected: (ControlModule?) -> Unit,
    showTwoPanes: Boolean,
    controlFocusRequesters: List<FocusRequester>,
    emptyDetailFocusRequester: FocusRequester,
    fanContent: @Composable () -> Unit,
    fanAction: @Composable () -> Unit,
    joystickContent: @Composable () -> Unit,
    joystickAction: @Composable () -> Unit,
    buttonLayoutContent: @Composable () -> Unit,
    buttonLayoutAction: @Composable () -> Unit,
    presetContent: @Composable () -> Unit,
    presetAction: @Composable () -> Unit,
    performanceContent: @Composable () -> Unit,
    performanceAction: @Composable () -> Unit,
) {
    BackHandler(enabled = !showTwoPanes && selectedControl != null) {
        onControlSelected(null)
    }
    if (showTwoPanes) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ControlListPane(
                selectedControl = selectedControl,
                onControlSelected = onControlSelected,
                focusRequesters = controlFocusRequesters,
                modifier = Modifier.width(360.dp),
            )
            selectedControl?.let { control ->
                ControlDetailPane(
                    control = control,
                    fanContent = fanContent,
                    fanAction = fanAction,
                    joystickContent = joystickContent,
                    joystickAction = joystickAction,
                    buttonLayoutContent = buttonLayoutContent,
                    buttonLayoutAction = buttonLayoutAction,
                    presetContent = presetContent,
                    presetAction = presetAction,
                    performanceContent = performanceContent,
                    performanceAction = performanceAction,
                    emptyDetailFocusRequester = emptyDetailFocusRequester,
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        )
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                        .padding(vertical = 16.dp),
                )
            }
        }
    } else if (selectedControl == null) {
        ControlListPane(
            selectedControl = null,
            onControlSelected = onControlSelected,
            focusRequesters = controlFocusRequesters,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        )
    } else {
        ControlDetailPane(
            control = selectedControl,
            fanContent = fanContent,
            fanAction = fanAction,
            joystickContent = joystickContent,
            joystickAction = joystickAction,
            buttonLayoutContent = buttonLayoutContent,
            buttonLayoutAction = buttonLayoutAction,
            presetContent = presetContent,
            presetAction = presetAction,
            performanceContent = performanceContent,
            performanceAction = performanceAction,
            emptyDetailFocusRequester = emptyDetailFocusRequester,
            onBack = { onControlSelected(null) },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(16.dp),
        )
    }
}

@Composable
private fun ControlListPane(
    selectedControl: ControlModule?,
    onControlSelected: (ControlModule) -> Unit,
    focusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FocusScrollMargin {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .focusGroup()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            CollapsingTopBar(
                title = stringResource(R.string.control_title),
                scrollBehavior = scrollBehavior,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                Spacer(Modifier.size(8.dp))
                ControlModuleRow(
                    control = ControlModule.PRESET,
                    index = 0,
                    count = 1,
                    selected = selectedControl == ControlModule.PRESET,
                    modifier = Modifier
                        .focusRequester(focusRequesters[ControlModule.PRESET.ordinal])
                        .focusProperties {
                            up = FocusRequester.Default
                            down = focusRequesters[ControlModule.FAN.ordinal]
                        },
                    onClick = { onControlSelected(ControlModule.PRESET) },
                )
                Spacer(Modifier.size(12.dp))
                val standardControls = listOf(
                    ControlModule.FAN,
                    ControlModule.JOYSTICK,
                    ControlModule.BUTTON_LAYOUT,
                    ControlModule.CORE,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    standardControls.forEachIndexed { index, control ->
                        ControlModuleRow(
                            control = control,
                            index = index,
                            count = standardControls.size,
                            selected = control == selectedControl,
                            modifier = Modifier
                                .focusRequester(focusRequesters[control.ordinal])
                                .focusProperties {
                                    up = if (index == 0) {
                                        focusRequesters[ControlModule.PRESET.ordinal]
                                    } else {
                                        focusRequesters[standardControls[index - 1].ordinal]
                                    }
                                    down = if (index == standardControls.lastIndex) {
                                        FocusRequester.Default
                                    } else {
                                        focusRequesters[standardControls[index + 1].ordinal]
                                    }
                                },
                            onClick = { onControlSelected(control) },
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun ControlModuleRow(
    control: ControlModule,
    index: Int,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
        shapes = settingsSegmentedShapes(
            index = index,
            count = count,
        ),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
            )
        },
        content = {
            Text(
                text = stringResource(control.label),
                style = if (selected) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.titleMedium
                },
            )
        },
    )
}

@Composable
private fun ControlDetailPane(
    control: ControlModule,
    fanContent: @Composable () -> Unit,
    fanAction: @Composable () -> Unit,
    joystickContent: @Composable () -> Unit,
    joystickAction: @Composable () -> Unit,
    buttonLayoutContent: @Composable () -> Unit,
    buttonLayoutAction: @Composable () -> Unit,
    presetContent: @Composable () -> Unit,
    presetAction: @Composable () -> Unit,
    performanceContent: @Composable () -> Unit,
    performanceAction: @Composable () -> Unit,
    emptyDetailFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusGroup(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        FocusScrollMargin {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 30.dp,
                            top = 30.dp,
                            end = 30.dp,
                            bottom = if (
                                control == ControlModule.FAN ||
                                    control == ControlModule.JOYSTICK ||
                                    control == ControlModule.BUTTON_LAYOUT ||
                                    control == ControlModule.PRESET ||
                                    control == ControlModule.CORE
                            ) 112.dp else 30.dp,
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.control_back),
                                )
                            }
                            Spacer(Modifier.size(4.dp))
                        }
                        PageTitle(stringResource(control.label))
                    }
                    if (control == ControlModule.FAN) {
                        Spacer(Modifier.size(18.dp))
                        fanContent()
                    } else if (control == ControlModule.JOYSTICK) {
                        Spacer(Modifier.size(18.dp))
                        joystickContent()
                    } else if (control == ControlModule.PRESET) {
                        Spacer(Modifier.size(18.dp))
                        presetContent()
                    } else if (control == ControlModule.BUTTON_LAYOUT) {
                        Spacer(Modifier.size(18.dp))
                        buttonLayoutContent()
                    } else if (control == ControlModule.CORE) {
                        Spacer(Modifier.size(18.dp))
                        performanceContent()
                    } else {
                        Spacer(
                            Modifier
                                .size(1.dp)
                                .focusRequester(emptyDetailFocusRequester)
                                .focusable()
                        )
                    }
                }
                if (control == ControlModule.FAN) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(30.dp),
                    ) {
                        fanAction()
                    }
                } else if (control == ControlModule.JOYSTICK) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(30.dp),
                    ) {
                        joystickAction()
                    }
                } else if (control == ControlModule.PRESET) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(30.dp),
                    ) {
                        presetAction()
                    }
                } else if (control == ControlModule.BUTTON_LAYOUT) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(30.dp),
                    ) {
                        buttonLayoutAction()
                    }
                } else if (control == ControlModule.CORE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(30.dp),
                    ) {
                        performanceAction()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsPage(
    selectedApp: String?,
    onAppSelected: (String?) -> Unit,
    installedApps: List<InstalledAppInfo>,
    showTwoPanes: Boolean,
    appFocusRequester: FocusRequester,
    appProfileContent: @Composable (String) -> Unit,
) {
    BackHandler(enabled = !showTwoPanes && selectedApp != null) {
        onAppSelected(null)
    }
    if (showTwoPanes) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppListPane(
                selectedApp = selectedApp,
                installedApps = installedApps,
                onAppSelected = { onAppSelected(it) },
                focusRequester = appFocusRequester,
                modifier = Modifier.width(360.dp),
            )
            selectedApp?.let { appKey ->
                AppDetailPane(
                    title = installedApps.firstOrNull { it.packageName == appKey }?.label
                        ?: appKey,
                    content = { appProfileContent(appKey) },
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        )
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                        .padding(vertical = 16.dp),
                )
            }
        }
    } else if (selectedApp == null) {
        AppListPane(
            selectedApp = null,
            installedApps = installedApps,
            onAppSelected = { onAppSelected(it) },
            focusRequester = appFocusRequester,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        )
    } else {
        AppDetailPane(
            title = installedApps.firstOrNull { it.packageName == selectedApp }?.label
                ?: selectedApp,
            onBack = { onAppSelected(null) },
            content = { appProfileContent(selectedApp) },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(16.dp),
        )
    }
}

@Composable
private fun AppListPane(
    selectedApp: String?,
    installedApps: List<InstalledAppInfo>,
    onAppSelected: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var appFilter by rememberSaveable { mutableStateOf(AppListFilter.GAME) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val filteredApps = remember(installedApps, query, appFilter) {
        val normalized = query.trim()
        installedApps.filter { app ->
            val matchesCategory = when (appFilter) {
                AppListFilter.GAME -> app.isGame
                AppListFilter.OTHER -> !app.isGame
                AppListFilter.ALL -> true
            }
            val matchesQuery = normalized.isEmpty() ||
                app.label.contains(normalized, ignoreCase = true) ||
                app.packageName.contains(normalized, ignoreCase = true)
            matchesQuery && (normalized.isNotEmpty() || matchesCategory)
        }
    }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocusRequester.requestFocus()
    }
    BackHandler(enabled = searchFieldFocused || searchExpanded) {
        keyboardController?.hide()
        focusManager.clearFocus()
        searchExpanded = false
        query = ""
    }
    FocusScrollMargin {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .focusGroup()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            CollapsingTopBar(
                title = stringResource(R.string.nav_apps),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = { searchExpanded = true },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_apps),
                            tint = if (appFilter == AppListFilter.ALL) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { filterMenuExpanded = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter_alt),
                                contentDescription = stringResource(R.string.filter_apps),
                                tint = if (appFilter == AppListFilter.ALL) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                            modifier = Modifier.width(220.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            val filters = listOf(
                                AppListFilter.GAME to R.string.filter_games,
                                AppListFilter.OTHER to R.string.filter_other,
                                AppListFilter.ALL to R.string.filter_all,
                            )
                            filters.forEach { (filter, label) ->
                                val selected = appFilter == filter
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(label),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        appFilter = filter
                                        filterMenuExpanded = false
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                Color.Transparent
                                            }
                                        ),
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                },
                actionsEndPadding = 8.dp,
            )
            if (searchExpanded) {
                Spacer(Modifier.size(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .focusRequester(searchFocusRequester)
                        .onFocusChanged { searchFieldFocused = it.isFocused },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                if (filteredApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        filteredApps.forEachIndexed { index, app ->
                            SegmentedListItem(
                                selected = selectedApp == app.packageName,
                                onClick = { onAppSelected(app.packageName) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (index == 0) Modifier.focusRequester(focusRequester)
                                        else Modifier
                                    )
                                    .bringIntoViewOnFocus(),
                                shapes = settingsSegmentedShapes(
                                    index = index,
                                    count = filteredApps.size,
                                ),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                                leadingContent = {
                                    app.icon?.let { bitmap ->
                                        Image(
                                            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape),
                                        )
                                    }
                                },
                                supportingContent = {
                                    Text(
                                        text = app.profileSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                },
                                content = {
                                    Text(
                                        text = app.label,
                                        style = if (selectedApp == app.packageName) {
                                            MaterialTheme.typography.titleMediumEmphasized
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.size(14.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun AppDetailPane(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusGroup(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        FocusScrollMargin {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(30.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.control_back),
                            )
                        }
                        Spacer(Modifier.size(4.dp))
                    }
                    PageTitle(title)
                }
                Spacer(Modifier.size(18.dp))
                content()
            }
        }
    }
}

@Composable
private fun CollapsingTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    horizontalPadding: Dp = 16.dp,
    actions: @Composable () -> Unit = {},
    actionsEndPadding: Dp = horizontalPadding,
) {
    val density = LocalDensity.current
    val collapseRange = with(density) { (152.dp - 64.dp).toPx() }
    SideEffect {
        scrollBehavior.state.heightOffsetLimit = -collapseRange
    }
    val collapsedFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val titleStyle = lerpTextStyle(
        MaterialTheme.typography.displayMediumEmphasized,
        MaterialTheme.typography.titleLargeEmphasized,
        collapsedFraction,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(
            Modifier.windowInsetsTopHeight(
                WindowInsets.statusBars.only(WindowInsetsSides.Top)
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lerpDp(152.dp, 64.dp, collapsedFraction)),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = lerpDp(28.dp, 18.dp, collapsedFraction),
                ),
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = actionsEndPadding,
                        bottom = lerpDp(8.dp, 8.dp, collapsedFraction),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLargeEmphasized,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
