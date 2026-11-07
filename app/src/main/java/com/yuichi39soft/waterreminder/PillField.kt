package com.yuichi39soft.waterreminder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * 胶囊形输入框。
 * 顶部边框上的"缺口"按标题宽度计算，左右留白一致，
 * 这样标题往右放时，左侧那段框线不会被空出来。
 */
@Composable
fun PillNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelMedium
    val dens = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelLayout = remember(label, labelStyle) { measurer.measure(label, labelStyle) }
    val labelWidth = labelLayout.size.width.toFloat()
    val labelHeight = labelLayout.size.height.toFloat()

    val strokePx = with(dens) { 1.2.dp.toPx() }
    val labelStartPx = with(dens) { 24.dp.toPx() }
    val gapPadPx = with(dens) { 5.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBehind {
                val inset = strokePx / 2f
                val w = size.width
                val h = size.height
                val r = h / 2f
                val gapStart = (labelStartPx - gapPadPx).coerceAtLeast(inset)
                val gapEnd = (labelStartPx + labelWidth + gapPadPx).coerceAtMost(w - r)

                val path = Path().apply {
                    moveTo(gapStart, inset)
                    lineTo(w - r, inset)
                    arcTo(Rect(w - h, 0f, w, h), -90f, 90f, false)
                    arcTo(Rect(w - h, 0f, w, h), 0f, 90f, false)
                    lineTo(r, h - inset)
                    arcTo(Rect(0f, 0f, h, h), 90f, 90f, false)
                    arcTo(Rect(0f, 0f, h, h), 180f, 90f, false)
                    lineTo(gapStart, inset)
                }
                drawPath(path, outline, style = Stroke(width = strokePx))
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = label,
            style = labelStyle,
            color = outline,
            modifier = Modifier.offset(
                x = 24.dp,
                y = with(dens) { (-(labelHeight / 2f) + strokePx).toDp() }
            )
        )
    }
}
