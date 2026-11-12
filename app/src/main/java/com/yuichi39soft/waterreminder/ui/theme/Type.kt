package com.yuichi39soft.waterreminder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.yuichi39soft.waterreminder.R

/**
 * 全局字体：Google Sans Code。
 * 文件随 APK 打包（res/font），是可变字体（wght / MONO 轴），
 * 这里按字重注册同一份文件，系统按请求的字重取对应的轴实例。
 *
 * 为了体积，字体做过子集化（见 tools/subset_font.py），只保留 ASCII 与少量符号；
 * 中文等子集里没有的字符会按字符回退到系统字体，这是预期行为。
 */
val GoogleSansCode = FontFamily(
    Font(R.font.google_sans_code, FontWeight.Thin),
    Font(R.font.google_sans_code, FontWeight.ExtraLight),
    Font(R.font.google_sans_code, FontWeight.Light),
    Font(R.font.google_sans_code, FontWeight.Normal),
    Font(R.font.google_sans_code, FontWeight.Medium),
    Font(R.font.google_sans_code, FontWeight.SemiBold),
    Font(R.font.google_sans_code, FontWeight.Bold),
    Font(R.font.google_sans_code, FontWeight.ExtraBold),
    Font(R.font.google_sans_code, FontWeight.Black)
)

private val Base = Typography()

/** 把 Material3 的 15 个文本样式全部换成 Google Sans Code，尺寸沿用 Material3 默认值 */
val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = GoogleSansCode),
    displayMedium = Base.displayMedium.copy(fontFamily = GoogleSansCode),
    displaySmall = Base.displaySmall.copy(fontFamily = GoogleSansCode),
    headlineLarge = Base.headlineLarge.copy(fontFamily = GoogleSansCode),
    headlineMedium = Base.headlineMedium.copy(fontFamily = GoogleSansCode),
    headlineSmall = Base.headlineSmall.copy(fontFamily = GoogleSansCode),
    titleLarge = Base.titleLarge.copy(fontFamily = GoogleSansCode),
    titleMedium = Base.titleMedium.copy(fontFamily = GoogleSansCode),
    titleSmall = Base.titleSmall.copy(fontFamily = GoogleSansCode),
    bodyLarge = Base.bodyLarge.copy(fontFamily = GoogleSansCode),
    bodyMedium = Base.bodyMedium.copy(fontFamily = GoogleSansCode),
    bodySmall = Base.bodySmall.copy(fontFamily = GoogleSansCode),
    labelLarge = Base.labelLarge.copy(fontFamily = GoogleSansCode),
    labelMedium = Base.labelMedium.copy(fontFamily = GoogleSansCode),
    labelSmall = Base.labelSmall.copy(fontFamily = GoogleSansCode)
)
