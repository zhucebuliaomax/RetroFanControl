package com.mmax.retrocontrol.hardware

import android.util.Log
import com.mmax.retrocontrol.data.CpuFrequencyPolicy
import com.mmax.retrocontrol.data.PerformanceProfile
import com.topjohnwu.superuser.Shell

object CpuFrequencyController {
    private const val TAG = "CpuFrequencyController"
    private const val POLICY_ROOT = "/sys/devices/system/cpu/cpufreq"

    @Volatile
    private var cachedPolicies: List<CpuFrequencyPolicy> = emptyList()

    data class ApplyResult(
        val actualFrequencies: Map<Int, Int>,
        val verificationPassed: Boolean,
    )

    fun detectPolicies(forceRefresh: Boolean = false): List<CpuFrequencyPolicy> {
        if (!forceRefresh) cachedPolicies.takeIf(List<CpuFrequencyPolicy>::isNotEmpty)?.let {
            return it
        }
        val result = Shell.cmd(discoveryScript()).exec()
        if (!result.isSuccess) {
            Log.e(TAG, "Unable to discover CPU frequency policies: ${result.err.joinToString()}")
            return emptyList()
        }
        return parsePolicyLines(result.out).also { policies ->
            if (policies.isNotEmpty()) {
                cachedPolicies = policies
                Log.i(TAG, "Discovered CPU frequency policies: ${policies.map { it.id }}")
            }
        }
    }

    fun applyProfile(
        profile: PerformanceProfile,
        policies: List<CpuFrequencyPolicy> = detectPolicies(),
    ): Result<ApplyResult> = runCatching {
        require(policies.isNotEmpty()) { "No CPU frequency policies were found" }
        require(profile.maxFrequencies.isNotEmpty()) { "The performance profile is empty" }

        val script = buildApplyScript(profile, policies)
        val commandResult = Shell.cmd(script).exec()
        check(commandResult.isSuccess) {
            commandResult.err.joinToString().ifBlank { "Unable to write CPU frequency limits" }
        }
        val actual = readCurrentMaxFrequencies(policies)
        val verified = policies.all { policy ->
            val requested = profile.maxFrequencies[policy.id] ?: return@all false
            val value = actual[policy.id] ?: return@all false
            if (profile.isStock) {
                val selectableMax = policy.supportedFrequencies.lastOrNull()
                    ?: policy.stockMaxFrequency
                value in selectableMax..policy.stockMaxFrequency
            } else {
                value == requested
            }
        }
        ApplyResult(actualFrequencies = actual, verificationPassed = verified).also {
            if (verified) {
                Log.i(TAG, "Applied performance profile ${profile.id}: $actual")
            } else {
                Log.w(TAG, "Frequency verification failed for ${profile.id}: $actual")
            }
        }
    }

    fun readCurrentMaxFrequencies(
        policies: List<CpuFrequencyPolicy> = detectPolicies(),
    ): Map<Int, Int> {
        if (policies.isEmpty()) return emptyMap()
        val command = policies.joinToString("; ") { policy ->
            "printf '${policy.id}='; cat ${policy.scalingMaxPath} 2>/dev/null"
        }
        val result = Shell.cmd(command).exec()
        if (!result.isSuccess) return emptyMap()
        return result.out.mapNotNull { line ->
            val id = line.substringBefore('=').trim().toIntOrNull() ?: return@mapNotNull null
            val frequency = line.substringAfter('=', "").trim().toIntOrNull()
                ?: return@mapNotNull null
            id to frequency
        }.toMap()
    }

    fun readCurrentFrequencies(cpuIds: Collection<Int>): Map<Int, Int> {
        val ids = cpuIds.filter { it >= 0 }.distinct().sorted()
        if (ids.isEmpty()) return emptyMap()
        val command = ids.joinToString("; ") { cpuId ->
            "value=\$(cat /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_cur_freq " +
                "2>/dev/null); printf '$cpuId=%s\\n' \"\$value\""
        }
        val result = Shell.cmd(command).exec()
        if (!result.isSuccess) return emptyMap()
        return result.out.mapNotNull { line ->
            val cpuId = line.substringBefore('=').trim().toIntOrNull()
                ?: return@mapNotNull null
            val frequency = line.substringAfter('=', "").trim().toIntOrNull()
                ?: return@mapNotNull null
            cpuId to frequency
        }.toMap()
    }

    internal fun parsePolicyLines(lines: List<String>): List<CpuFrequencyPolicy> {
        return lines.mapNotNull { rawLine ->
            val fields = rawLine.trim().split('|')
            if (fields.size != 7) return@mapNotNull null
            val id = fields[0].toIntOrNull() ?: return@mapNotNull null
            val cpuIds = parseNumbers(fields[1], allowZero = true).ifEmpty { listOf(id) }
            val scalingMin = fields[3].trim().toIntOrNull() ?: 0
            val scalingMax = fields[4].trim().toIntOrNull() ?: 0
            val cpuInfoMin = fields[5].trim().toIntOrNull() ?: 0
            val cpuInfoMax = fields[6].trim().toIntOrNull() ?: 0
            val supported = parseNumbers(fields[2]).ifEmpty {
                listOf(cpuInfoMin, scalingMin, scalingMax, cpuInfoMax)
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
            }
            val stockMax = cpuInfoMax.takeIf { it > 0 }
                ?: supported.lastOrNull()
                ?: scalingMax.takeIf { it > 0 }
                ?: return@mapNotNull null
            CpuFrequencyPolicy(
                id = id,
                cpuIds = cpuIds,
                supportedFrequencies = supported,
                currentMinFrequency = scalingMin.takeIf { it > 0 }
                    ?: supported.firstOrNull()
                    ?: cpuInfoMin,
                currentMaxFrequency = scalingMax.takeIf { it > 0 }
                    ?: supported.lastOrNull()
                    ?: stockMax,
                stockMinFrequency = cpuInfoMin.takeIf { it > 0 }
                    ?: supported.firstOrNull()
                    ?: scalingMin,
                stockMaxFrequency = stockMax,
            )
        }.sortedBy(CpuFrequencyPolicy::id)
    }

    internal fun buildApplyScript(
        profile: PerformanceProfile,
        policies: List<CpuFrequencyPolicy>,
    ): String = buildString {
        appendLine("#!/system/bin/sh")
        policies.forEach { policy ->
            val frequency = profile.maxFrequencies[policy.id] ?: return@forEach
            val maxPath = policy.scalingMaxPath
            val minPath = "${policy.path}/scaling_min_freq"
            if (profile.isStock) {
                appendLine("chmod 0644 $maxPath 2>/dev/null")
                appendLine("echo $frequency > $maxPath")
                appendLine("chmod 0644 $minPath 2>/dev/null")
                appendLine("echo ${policy.currentMinFrequency} > $minPath")
            } else {
                appendLine(
                    "current_min=\$(cat $minPath 2>/dev/null); " +
                        "if [ -n \"\$current_min\" ] && [ \"\$current_min\" -gt $frequency ]; " +
                        "then chmod 0644 $minPath 2>/dev/null; " +
                        "echo ${policy.stockMinFrequency} > $minPath; fi"
                )
                appendLine("chmod 0644 $maxPath 2>/dev/null")
                appendLine("echo $frequency > $maxPath")
                appendLine("chmod 0444 $maxPath 2>/dev/null")
            }
        }
    }

    private fun discoveryScript(): String = buildString {
        append("for d in $POLICY_ROOT/policy[0-9]*; do ")
        append("[ -d \"\$d\" ] || continue; ")
        append("id=\${d##*policy}; ")
        append("cpus=\$(cat \"\$d/affected_cpus\" 2>/dev/null); ")
        append("[ -n \"\$cpus\" ] || cpus=\$(cat \"\$d/related_cpus\" 2>/dev/null); ")
        append("available=\$(cat \"\$d/scaling_available_frequencies\" 2>/dev/null); ")
        append("minimum=\$(cat \"\$d/scaling_min_freq\" 2>/dev/null); ")
        append("maximum=\$(cat \"\$d/scaling_max_freq\" 2>/dev/null); ")
        append("stock_min=\$(cat \"\$d/cpuinfo_min_freq\" 2>/dev/null); ")
        append("stock_max=\$(cat \"\$d/cpuinfo_max_freq\" 2>/dev/null); ")
        append("printf '%s|%s|%s|%s|%s|%s|%s\\n' ")
        append("\"\$id\" \"\$cpus\" \"\$available\" \"\$minimum\" ")
        append("\"\$maximum\" \"\$stock_min\" \"\$stock_max\"; ")
        append("done")
    }

    private fun parseNumbers(value: String, allowZero: Boolean = false): List<Int> = value
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull(String::toIntOrNull)
        .filter { it > 0 || (allowZero && it == 0) }
        .distinct()
        .sorted()
}
