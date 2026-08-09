package com.mmax.retrocontrol.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.content.edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.data.CpuFrequencyPolicy
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.FanCurvePoint
import com.mmax.retrocontrol.hardware.TelemetryRepository
import com.mmax.retrocontrol.hardware.TemperatureSummary
import com.mmax.retrocontrol.util.formatTemperature
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class TelemetryOverlay(
    private val context: Context,
    private val onAdjustFan: (Int) -> Unit,
    private val onAdjustFrequency: (Int, Int) -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var host: OverlayViewHost? = null
    private var params: WindowManager.LayoutParams? = null
    private var posX = 100
    private var posY = 100

    fun show() {
        if (host != null) return
        posX = prefs.getInt(Prefs.OVERLAY_X, 100)
        posY = prefs.getInt(Prefs.OVERLAY_Y, 100)

        mainHandler.post {
            if (host != null) return@post
            val newHost = OverlayViewHost(context)
            val layout = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = posX
                y = posY
            }
            newHost.setContent {
                OverlayContent(
                    onDrag = ::moveBy,
                    onAdjustFan = onAdjustFan,
                    onAdjustFrequency = onAdjustFrequency,
                    onClose = {
                        prefs.edit { putBoolean(Prefs.OVERLAY_ENABLED, false) }
                    },
                )
            }
            runCatching {
                windowManager.addView(newHost.composeView, layout)
                newHost.onResumed()
                host = newHost
                params = layout
            }
        }
    }

    fun hide() {
        val current = host
        host = null
        params = null
        mainHandler.post {
            current?.let {
                runCatching { windowManager.removeView(it.composeView) }
                it.onDestroyed()
            }
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val layout = params ?: return
        posX += dx.roundToInt()
        posY += dy.roundToInt()
        layout.x = posX
        layout.y = posY
        mainHandler.post {
            host?.let {
                runCatching { windowManager.updateViewLayout(it.composeView, layout) }
                prefs.edit {
                    putInt(Prefs.OVERLAY_X, posX)
                    putInt(Prefs.OVERLAY_Y, posY)
                }
            }
        }
    }
}

/**
 * Ordered layout registry for the floating window. Adding another presentation
 * later only requires one enum entry and one rendering branch; cycling remains
 * unchanged and every newly opened overlay still starts in data-only mode.
 */
private enum class OverlayDisplayMode {
    DATA_ONLY,
    DATA_FAN_CURVE;

    fun next(): OverlayDisplayMode = entries[(ordinal + 1) % entries.size]
}

private data class OverlayCoreGroup(
    val labelRes: Int,
    val cpuIds: Set<Int>,
)

private val overlayCoreGroups = listOf(
    OverlayCoreGroup(R.string.e_cores, setOf(0, 1, 2)),
    OverlayCoreGroup(R.string.p_cores, setOf(3, 4, 5, 6)),
    OverlayCoreGroup(R.string.x_core, setOf(7)),
)

@Composable
private fun OverlayContent(
    onDrag: (Float, Float) -> Unit,
    onAdjustFan: (Int) -> Unit,
    onAdjustFrequency: (Int, Int) -> Unit,
    onClose: () -> Unit,
) {
    val telemetry by TelemetryRepository.state.collectAsState()
    val thermal = telemetry.thermal
    var displayMode by remember { mutableStateOf(OverlayDisplayMode.DATA_ONLY) }

    fun cycleDisplayMode() {
        displayMode = displayMode.next()
    }

    Box(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE6151B1C))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 9.dp, vertical = 10.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(
                modifier = Modifier.clickable(onClick = ::cycleDisplayMode),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color(0xFFFFB000),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onClose),
                    )
                }
                Spacer(Modifier.height(1.dp))
                OverlayTemperatureGroup(stringResource(R.string.cpu), thermal.cpuSummary)
                OverlayTemperatureGroup(stringResource(R.string.gpu), thermal.gpuSummary)
                Spacer(Modifier.height(1.dp))
                OverlayDualMetricGroup(
                    firstTitle = stringResource(R.string.ddr_short),
                    firstValue = thermal.ddr?.let { formatTemperature(it.tempC) }
                        ?: stringResource(R.string.not_available),
                    secondTitle = stringResource(R.string.battery_short),
                    secondValue = thermal.battery?.let { formatTemperature(it.tempC) }
                        ?: stringResource(R.string.not_available),
                )
                overlayCoreGroups.forEach { group ->
                    OverlayFrequencyGroup(
                        title = stringResource(group.labelRes),
                        frequencyKhz = averageFrequency(
                            telemetry.frequency.currentFrequenciesKhz,
                            group.cpuIds,
                        ),
                    )
                }
            }
            if (displayMode == OverlayDisplayMode.DATA_FAN_CURVE) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayFanButton(
                        increase = false,
                        enabled = telemetry.fanAdjustEnabled,
                        onClick = { onAdjustFan(-5) },
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.fan_speed),
                            color = Color(0xFFFFB000),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${telemetry.fanPercent}%",
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OverlayFanButton(
                        increase = true,
                        enabled = telemetry.fanAdjustEnabled,
                        onClick = { onAdjustFan(5) },
                    )
                }
                Spacer(Modifier.height(3.dp))
                overlayCoreGroups.forEach { group ->
                    val policy = telemetry.frequency.policies.policyFor(group.cpuIds)
                    OverlayFrequencyControl(
                        title = stringResource(group.labelRes),
                        policy = policy,
                        targetFrequencyKhz = policy?.let {
                            telemetry.frequency.targetMaxFrequenciesKhz[it.id]
                        },
                        enabled = telemetry.frequency.adjustEnabled,
                        onAdjust = { direction ->
                            policy?.let { onAdjustFrequency(it.id, direction) }
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                OverlayCurvePreview(
                    name = telemetry.activeCurveName,
                    points = telemetry.activeCurvePoints,
                    currentTempC = thermal.controlTempC,
                )
            }
        }
    }
}

@Composable
private fun OverlayCurvePreview(
    name: String,
    points: List<FanCurvePoint>,
    currentTempC: Double,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1AFFFFFF), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(
            text = name,
            color = Color(0xFFFFB000),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        val lineColor = Color(0xFFFFB000)
        val markerColor = Color(0xFFFF5D6C)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (points.size < 2) return@Canvas
            fun x(temp: Int): Float = ((temp - 20f) / 80f * size.width)
                .coerceIn(0f, size.width)
            fun y(percent: Int): Float = size.height -
                (percent / 100f * size.height).coerceIn(0f, size.height)

            drawLine(
                color = Color(0x24FFFFFF),
                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            )
            val path = Path()
            points.sortedBy { it.tempC }.forEachIndexed { index, point ->
                val pointX = x(point.tempC)
                val pointY = y(point.speedPercent)
                if (index == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x(point.tempC), y(point.speedPercent)),
                )
            }
            if (currentTempC in 20.0..100.0) {
                val currentX = ((currentTempC.toFloat() - 20f) / 80f * size.width)
                    .coerceIn(0f, size.width)
                drawLine(
                    color = markerColor,
                    start = androidx.compose.ui.geometry.Offset(currentX, 0f),
                    end = androidx.compose.ui.geometry.Offset(currentX, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
    }
}

@Composable
private fun OverlayFanButton(
    increase: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) Color(0x99FFFFFF) else Color(0x33FFFFFF),
        ),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color(0x55FFFFFF),
        ),
    ) {
        Icon(
            imageVector = if (increase) Icons.Default.Add else Icons.Default.Remove,
            contentDescription = stringResource(
                if (increase) R.string.increase_fan_speed else R.string.decrease_fan_speed
            ),
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun OverlayFrequencyControl(
    title: String,
    policy: CpuFrequencyPolicy?,
    targetFrequencyKhz: Int?,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
) {
    val frequencies = policy?.supportedFrequencies.orEmpty()
    val selectedIndex = targetFrequencyKhz?.let { target ->
        frequencies.indices.minByOrNull { index ->
            abs(frequencies[index].toLong() - target.toLong())
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayFrequencyButton(
            increase = false,
            enabled = enabled && selectedIndex != null && selectedIndex > 0,
            description = stringResource(R.string.decrease_core_frequency, title),
            onClick = { onAdjust(-1) },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color(0xFFFFB000),
                fontSize = 9.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = targetFrequencyKhz?.let(::formatOverlayFrequency)
                    ?: stringResource(R.string.not_available),
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        OverlayFrequencyButton(
            increase = true,
            enabled = enabled && selectedIndex != null && selectedIndex < frequencies.lastIndex,
            description = stringResource(R.string.increase_core_frequency, title),
            onClick = { onAdjust(1) },
        )
    }
}

@Composable
private fun OverlayFrequencyButton(
    increase: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) Color(0x99FFFFFF) else Color(0x33FFFFFF),
        ),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color(0x55FFFFFF),
        ),
    ) {
        Icon(
            imageVector = if (increase) Icons.Default.Add else Icons.Default.Remove,
            contentDescription = description,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun OverlayTemperatureGroup(
    title: String,
    summary: TemperatureSummary,
) {
    val maxColor = tempColor(summary.maxC)
    val averageLabel = stringResource(R.string.average_short)
    val maximumLabel = stringResource(R.string.maximum_short)
    val unavailable = stringResource(R.string.not_available)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1AFFFFFF), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFFFB000), fontWeight = FontWeight.Bold)) {
                    append("$title: ")
                }
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append(
                        if (summary.count > 0) {
                            "$averageLabel ${formatTemperature(summary.averageC)}"
                        } else {
                            "$averageLabel $unavailable"
                        }
                    )
                }
            },
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            if (summary.count > 0) {
                "$maximumLabel: ${summary.hottest?.name ?: unavailable}  " +
                    formatTemperature(summary.maxC)
            } else {
                "$maximumLabel: $unavailable"
            },
            color = maxColor,
            fontSize = 9.5.sp,
            lineHeight = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun OverlayDualMetricGroup(
    firstTitle: String,
    firstValue: String,
    secondTitle: String,
    secondValue: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1AFFFFFF), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OverlayCompactMetric(firstTitle, firstValue)
        OverlayCompactMetric(secondTitle, secondValue)
    }
}

@Composable
private fun OverlayFrequencyGroup(title: String, frequencyKhz: Int?) {
    val unavailable = stringResource(R.string.not_available)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFFFFB000), fontWeight = FontWeight.Bold)) {
                append("$title: ")
            }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append(
                    frequencyKhz?.let(::formatOverlayFrequency)
                        ?: unavailable
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1AFFFFFF), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        maxLines = 1,
    )
}

@Composable
private fun OverlayCompactMetric(title: String, value: String) {
    Text(
        text = "$title: $value",
        color = Color.White,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun averageFrequency(values: Map<Int, Int>, cpuIds: Set<Int>): Int? = cpuIds
    .mapNotNull(values::get)
    .filter { it > 0 }
    .takeIf { it.isNotEmpty() }
    ?.average()
    ?.roundToInt()

private fun List<CpuFrequencyPolicy>.policyFor(cpuIds: Set<Int>): CpuFrequencyPolicy? =
    filter { policy -> policy.cpuIds.any(cpuIds::contains) }
        .maxByOrNull { policy -> policy.cpuIds.count(cpuIds::contains) }

private fun formatOverlayFrequency(frequencyKhz: Int): String = when {
    frequencyKhz >= 1_000_000 -> String.format(
        Locale.getDefault(),
        "%.2f GHz",
        frequencyKhz / 1_000_000.0,
    )
    else -> String.format(Locale.getDefault(), "%.0f MHz", frequencyKhz / 1_000.0)
}

private fun tempColor(value: Double): Color = when {
    value >= 80.0 -> Color(0xFFFF5D6C)
    value >= 65.0 -> Color(0xFFFFB000)
    else -> Color.White
}
