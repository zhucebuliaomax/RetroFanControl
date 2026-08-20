package com.mmax.retrocontrol.service

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.mmax.retrocontrol.data.AmbilightPreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.tile.AmbilightQuickSettingsTile
import java.util.concurrent.atomic.AtomicBoolean

class MediaProjectionActivity : androidx.activity.ComponentActivity() {
    private var ownsCaptureRequest = false

    private val captureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val token = result.data?.takeIf { result.resultCode == RESULT_OK }
        token?.let {
            projectionGrantPending.set(true)
            captureRequestInFlight.set(false)
            AmbilightPreferences.setEnabled(
                getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                true,
            )
            startForegroundService(
                Intent(this, SystemControlService::class.java)
                    .setAction(SystemControlService.ACTION_SET_PROJECTION_INTENT)
                    .putExtra(SystemControlService.EXTRA_PROJECTION_INTENT, it)
            )
        } ?: run {
            captureRequestInFlight.set(false)
            AmbilightPreferences.setEnabled(
                getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                false,
            )
            AmbilightQuickSettingsTile.requestRefresh(applicationContext)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null && captureRequestInFlight.get()) {
            ownsCaptureRequest = true
            return
        }
        if (!captureRequestInFlight.compareAndSet(false, true)) {
            finish()
            return
        }
        ownsCaptureRequest = true
        val manager = getSystemService(MediaProjectionManager::class.java)
        runCatching { captureLauncher.launch(manager.createScreenCaptureIntent()) }
            .onFailure {
                captureRequestInFlight.set(false)
                AmbilightPreferences.setEnabled(
                    getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
                    false,
                )
                AmbilightQuickSettingsTile.requestRefresh(applicationContext)
                finish()
            }
    }

    override fun onDestroy() {
        if (ownsCaptureRequest && isFinishing) captureRequestInFlight.set(false)
        super.onDestroy()
    }

    companion object {
        private val captureRequestInFlight = AtomicBoolean(false)
        private val projectionGrantPending = AtomicBoolean(false)

        fun isCaptureSessionPending(): Boolean =
            captureRequestInFlight.get() || projectionGrantPending.get()

        fun markProjectionGrantConsumed() {
            projectionGrantPending.set(false)
        }

        fun createIntent(context: Context): Intent =
            Intent(context, MediaProjectionActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
    }
}
