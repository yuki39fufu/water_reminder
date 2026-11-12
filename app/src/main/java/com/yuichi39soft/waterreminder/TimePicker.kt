package com.yuichi39soft.waterreminder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 时间选择：上方两个半胶囊输入，下方表盘 */
@Composable
fun WaterTimePickerDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val now = Calendar.getInstance()
    var hour by rememberSaveable { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by rememberSaveable { mutableIntStateOf(now.get(Calendar.MINUTE)) }
    var selectingHour by rememberSaveable { mutableStateOf(true) }
    var hourText by rememberSaveable { mutableStateOf(String.format("%02d", hour)) }
    var minuteText by rememberSaveable { mutableStateOf(String.format("%02d", minute)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置时间") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TimeHalfField(
                        text = hourText,
                        pillOnLeft = true,
                        selected = selectingHour,
                        onSelect = { selectingHour = true },
                        onTextChange = { input ->
                            if (input.length <= 2 && input.all { it.isDigit() }) {
                                hourText = input
                                input.toIntOrNull()?.let { if (it in 0..23) hour = it }
                            }
                        }
                    )
                    Text(
                        ":",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    TimeHalfField(
                        text = minuteText,
                        pillOnLeft = false,
                        selected = !selectingHour,
                        onSelect = { selectingHour = false },
                        onTextChange = { input ->
                            if (input.length <= 2 && input.all { it.isDigit() }) {
                                minuteText = input
                                input.toIntOrNull()?.let { if (it in 0..59) minute = it }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(18.dp))

                ClockDial(
                    selectingHour = selectingHour,
                    hour = hour,
                    minute = minute,
                    onHour = {
                        hour = it
                        hourText = String.format("%02d", it)
                    },
                    onMinute = {
                        minute = it
                        minuteText = String.format("%02d", it)
                    },
                    modifier = Modifier.size(256.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 半个胶囊的输入框：左边那个左侧是圆的，右边那个右侧是圆的 */
@Composable
private fun TimeHalfField(
    text: String,
    pillOnLeft: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val shape = if (pillOnLeft) {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 12, bottomEndPercent = 12)
    } else {
        RoundedCornerShape(topStartPercent = 12, bottomStartPercent = 12, topEndPercent = 50, bottomEndPercent = 50)
    }
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(72.dp)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            readOnly = !selected,
            textStyle = MaterialTheme.typography.displaySmall.copy(
                color = fg,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // 未选中时在最上层盖一层点击区：只切换单位，不弹键盘
        if (!selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onSelect() }
            )
        }
    }
}

/** 表盘：小时模式内外两圈（1-12 / 13-23,0），分钟模式一圈 00-55 */
@Composable
private fun ClockDial(
    selectingHour: Boolean,
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dialColor = MaterialTheme.colorScheme.surfaceVariant
    val numberColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectorColor = MaterialTheme.colorScheme.primary
    val selectorTextColor = MaterialTheme.colorScheme.onPrimary
    val measurer = rememberTextMeasurer()

    val textStyle = TextStyle(fontSize = 14.sp, color = numberColor, fontWeight = FontWeight.Medium)

    fun pick(position: Offset, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val dx = position.x - cx
        val dy = position.y - cy
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val radius = kotlin.math.min(w, h) / 2f
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (angle < 0f) angle += 360f
        val index = ((angle / 30f).roundToInt()) % 12

        if (!selectingHour) {
            onMinute(index * 5)
            return
        }
        if (dist < radius * 0.72f) {
            // 内圈 1-12
            onHour(if (index == 0) 12 else index)
        } else {
            // 外圈：0 点在 12 点方向，其余 13..23
            onHour(if (index == 0) 0 else 12 + index)
        }
    }

    // 指针角度动画（走最短方向，避免转一大圈）
    val targetAngle = if (selectingHour) {
        when {
            hour in 1..12 -> ((hour % 12) * 30).toFloat()
            hour == 0 -> 0f
            else -> ((hour - 12) * 30).toFloat()
        }
    } else {
        (minute * 6).toFloat()
    }
    var animatedAngle by remember { mutableFloatStateOf(targetAngle) }
    LaunchedEffect(targetAngle, selectingHour) {
        val start = animatedAngle
        val delta = ((targetAngle - start + 540f) % 360f) - 180f
        animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { fraction, _ ->
            animatedAngle = start + delta * fraction
        }
    }
    // 指针半径：小时在内圈，分钟在外圈
    val targetRadiusFactor = when {
        !selectingHour -> 0.78f
        hour in 1..12 -> 0.56f
        else -> 0.82f
    }
    val selectorRadiusFactor by animateFloatAsState(
        targetValue = targetRadiusFactor,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "selector-radius"
    )
    // 两套数字交叉淡入淡出
    val hourNumbersAlpha by animateFloatAsState(
        targetValue = if (selectingHour) 1f else 0f,
        animationSpec = tween(200),
        label = "hour-numbers"
    )
    val minuteNumbersAlpha by animateFloatAsState(
        targetValue = if (selectingHour) 0f else 1f,
        animationSpec = tween(200),
        label = "minute-numbers"
    )

    Canvas(
        modifier = modifier
            .pointerInput(selectingHour) {
                detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(selectingHour) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position, size.width.toFloat(), size.height.toFloat())
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        drawCircle(color = dialColor, radius = radius, center = Offset(cx, cy))


        if (hourNumbersAlpha > 0.01f) {
            val color = numberColor.copy(alpha = hourNumbersAlpha)
            // 内圈 1-12
            for (v in 1..12) {
                val angle = Math.toRadians((-90.0 + (v % 12) * 30.0))
                val r = radius * 0.56f
                val pos = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
                drawNumber(measurer, v.toString(), pos, textStyle, color)
            }
            // 外圈：00 在 12 点方向，13..23 顺时针依次排开
            for (i in 0..11) {
                val v = if (i == 0) 0 else 12 + i
                val angle = Math.toRadians((-90.0 + i * 30.0))
                val r = radius * 0.82f
                val pos = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
                drawNumber(measurer, if (v < 10) "0$v" else v.toString(), pos, textStyle, color)
            }
        }
        if (minuteNumbersAlpha > 0.01f) {
            val color = numberColor.copy(alpha = minuteNumbersAlpha)
            for (i in 0..11) {
                val angle = Math.toRadians((-90.0 + i * 30.0))
                val r = radius * 0.78f
                val pos = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
                drawNumber(measurer, String.format("%02d", i * 5), pos, textStyle, color)
            }
        }

        // 选中位置：圆点 + 指针
        val angle = Math.toRadians((-90.0 + animatedAngle).toDouble())
        val selR = radius * selectorRadiusFactor
        val selPos = Offset(cx + selR * cos(angle).toFloat(), cy + selR * sin(angle).toFloat())
        drawLine(
            color = selectorColor,
            start = Offset(cx, cy),
            end = selPos,
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(color = selectorColor, radius = 17.dp.toPx(), center = selPos)
        val selText = if (selectingHour) String.format("%02d", hour) else String.format("%02d", minute)
        drawNumber(measurer, selText, selPos, textStyle.copy(color = selectorTextColor, fontSize = 14.sp), selectorTextColor)
        drawCircle(color = selectorColor, radius = 4.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun DrawScope.drawNumber(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    color: Color
) {
    val layout = measurer.measure(text, style.copy(color = color))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
