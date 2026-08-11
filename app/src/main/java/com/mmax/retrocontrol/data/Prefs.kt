package com.mmax.retrocontrol.data

object Prefs {
    const val FILE = "retro_control"
    const val KERNEL_FAN_TRIP_ORIGINAL_PREFIX = "kernel_fan_trip_original_v1_"

    /** "OFF" or an active profile id. Kept under the legacy key for migration compatibility. */
    const val FAN_MODE = "fan_mode"
    const val LAST_FAN_CURVE = "last_fan_curve"
    const val FAN_CURVE_CATALOG = "fan_curve_catalog_v2"
    const val PRESET_CATALOG = "control_preset_catalog_v1"
    const val SELECTED_PRESET = "selected_control_preset"
    const val SELECTED_GAME_PROFILE = "selected_game_profile"
    const val SELECTED_NON_GAME_PROFILE = "selected_non_game_profile"
    const val FAN_SELECTION_SOURCE = "fan_selection_source"
    const val FAN_SELECTION_CURVE = "fan_selection_curve"
    const val FAN_TILE_ENABLED = "fan_tile_enabled"
    const val APP_PROFILE_CATALOG = "app_profile_catalog_v1"
    const val JOYSTICK_PROFILE_CATALOG = "joystick_profile_catalog_v1"
    const val BUTTON_LAYOUT_PROFILE_CATALOG = "button_layout_profile_catalog_v1"
    const val BUTTON_LAYOUT_TILE_PROFILE = "button_layout_tile_profile"
    const val JOYSTICK_SELECTION_SOURCE = "joystick_selection_source"
    const val JOYSTICK_SELECTION_PROFILE = "joystick_selection_profile"
    const val JOYSTICK_TILE_ENABLED = "joystick_tile_enabled"
    const val PERFORMANCE_PROFILE_CATALOG = "performance_profile_catalog_v1"
    const val PERFORMANCE_TILE_PROFILE = "performance_tile_profile"
    const val LAST_APPLIED_PERFORMANCE_PROFILE = "last_applied_performance_profile"

    // Version 1 migration keys.
    const val FAN_CURVE_QUIET = "fan_curve_quiet"
    const val FAN_CURVE_NORMAL = "fan_curve_normal"
    const val FAN_CURVE_PERFORMANCE = "fan_curve_performance"
    const val FAN_CURVE_CUSTOM = "fan_curve_custom"
    const val LEGACY_FAN_CURVE_CUSTOM = "fan_curve_points"

    const val OVERLAY_ENABLED = "overlay_enabled"
    const val OVERLAY_X = "overlay_x"
    const val OVERLAY_Y = "overlay_y"
    const val OVERLAY_DISPLAY_MODE = "overlay_display_mode"
    const val AUTO_START_ENABLED = "auto_start_enabled"
    const val PROFILE_SWITCH_TOASTS_ENABLED =
        "profile_switch_notifications_enabled"
    const val USB_THERMAL_DISABLED = "usb_thermal_disabled"
    const val THERMAL_PROTECTION_DISABLED = "thermal_protection_disabled"
    const val ROOT_NOTICE_ACKNOWLEDGED = "root_notice_acknowledged"
    const val FIRST_LAUNCH_ACCESS_SHOWN = "first_launch_access_shown"

    fun isKernelFanTripBackup(key: String): Boolean =
        key.startsWith(KERNEL_FAN_TRIP_ORIGINAL_PREFIX)
}
