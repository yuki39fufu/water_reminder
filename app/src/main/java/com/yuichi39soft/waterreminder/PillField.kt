package com.yuichi39soft.waterreminder

import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * 胶囊输入框。
 * 边框是完整的一圈；标题文字带一块与背景同色的底色，压住穿过它的那一段线。
 * 层级：标题文字 > 标题底色 > 胶囊框线。
 */
@Composable
fun PillOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    val outline = MaterialTheme.colorScheme.outline
    // 与弹窗背景同色；换主题/纯黑时会自动跟着变
    val labelBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelStyle = MaterialTheme.typography.labelMedium
    val dens = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelLayout = remember(label, labelStyle) { measurer.measure(label, labelStyle) }
    val labelHalf = with(dens) { (labelLayout.size.height / 2f).toDp() }
    val strokePx = with(dens) { 1.2.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        // 最底层：完整的胶囊框线
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokePx
            drawRoundRect(
                color = outline,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius((size.height - stroke) / 2f),
                style = Stroke(width = stroke)
            )
        }

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
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 最上层：标题文字 + 同色底，压住线
        Text(
            text = label,
            style = labelStyle,
            color = outline,
            modifier = Modifier
                .offset(x = 24.dp, y = -labelHalf)
                .background(labelBg)
                .padding(horizontal = 4.dp)
        )
    }
}
