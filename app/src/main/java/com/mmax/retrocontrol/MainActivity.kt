package com.mmax.retrocontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mmax.retrocontrol.data.JoystickProfilePreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.feature.joystick.JoystickRgbMode
import com.mmax.retrocontrol.hardware.JoystickEffectEngine
import com.mmax.retrocontrol.service.MediaProjectionActivity
import com.mmax.retrocontrol.service.SystemControlService
import com.mmax.retrocontrol.theme.RetroControlTheme
import com.mmax.retrocontrol.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val startOnAccess = !prefs.getBoolean(Prefs.FIRST_LAUNCH_ACCESS_SHOWN, false)
        if (startOnAccess) {
            prefs.edit { putBoolean(Prefs.FIRST_LAUNCH_ACCESS_SHOWN, true) }
        }
        var showRootNotice by mutableStateOf(
            !prefs.getBoolean(Prefs.ROOT_NOTICE_ACKNOWLEDGED, false)
        )
        setContent {
            RetroControlTheme {
                DashboardScreen(
                    startOnAccess = startOnAccess,
                    onProfileSwitchToastsEnabled = ::onProfileSwitchToastsEnabled,
                    onRefreshRoot = { requestRoot(forceRefresh = true) },
                )
                if (showRootNotice) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.root_permission)) },
                        text = { Text(stringResource(R.string.root_notice_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    prefs.edit {
                                        putBoolean(Prefs.ROOT_NOTICE_ACKNOWLEDGED, true)
                                    }
                                    showRootNotice = false
                                    requestRoot(forceRefresh = true)
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        },
                    )
                }
            }
        }
        if (!showRootNotice) {
            requestRoot(forceRefresh = false)
        }
    }

    private fun requestRoot(forceRefresh: Boolean) {
        RootAccessManager.ensureRoot(forceRefresh = forceRefresh) {
            SystemControlService.startOrUpdate(applicationContext)
            requestAmbilightCaptureIfNeeded()
        }
    }

    private fun requestAmbilightCaptureIfNeeded() {
        if (JoystickEffectEngine.mediaProjectionActive) return
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val startsWithAmbilight = JoystickProfilePreferences.resolveEffectiveProfile(
            prefs = prefs,
            foregroundPackageName = null,
        )?.mode == JoystickRgbMode.AMBILIGHT
        if (JoystickEffectEngine.captureRequired || startsWithAmbilight) {
            startActivity(MediaProjectionActivity.createIntent(this))
        }
    }

    private fun onProfileSwitchToastsEnabled(enabled: Boolean) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return

        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
