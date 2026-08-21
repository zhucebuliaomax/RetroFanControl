package com.mmax.retrocontrol.hardware

import com.topjohnwu.superuser.Shell

/** Controls RP6 battery charge current without overriding firmware thermal protection. */
object ChargeSpeedController {
    internal const val CHARGE_CONTROL_LIMIT_PATH =
        "/sys/class/power_supply/battery/charge_control_limit"
    internal const val CHARGE_CONTROL_LIMIT_MAX_PATH =
        "/sys/class/power_supply/battery/charge_control_limit_max"

    internal const val RAPID_CHARGE_LEVEL = 0

    // RP6 firmware exposes a 7.2 A maximum with 0.5 A steps. Level 10 is 2.2 A,
    // which measured approximately 10 W at the USB input while retaining PD negotiation.
    internal const val SLOW_CHARGE_LEVEL = 10

    @Synchronized
    fun setSlowChargingEnabled(enabled: Boolean): Result<Boolean> = runCatching {
        val requested = if (enabled) SLOW_CHARGE_LEVEL else RAPID_CHARGE_LEVEL
        val result = Shell.cmd(
            "max=\$(cat $CHARGE_CONTROL_LIMIT_MAX_PATH 2>/dev/null) && " +
                "[ \"\$max\" -ge \"$requested\" ] && " +
                "echo $requested > $CHARGE_CONTROL_LIMIT_PATH && " +
                "cat $CHARGE_CONTROL_LIMIT_PATH 2>/dev/null",
        ).exec()
        check(result.isSuccess) {
            result.err.joinToString().ifBlank { "Unable to control charging speed" }
        }
        val actual = result.out.lastOrNull()?.trim()?.toIntOrNull()
        check(actual == requested) {
            "Charge-speed verification failed: requested=$requested actual=$actual"
        }
        enabled
    }
}
