package com.yuichi39soft.waterreminder

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** 设置弹层的内容：外观（共用）+ 当前功能的设置 + 页脚（共用） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    screen: AppScreen,
    water: WaterTrackerViewModel,
    pomodoro: PomodoroViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BottomSheetDefaults.DragHandle()
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            AppearanceSection(water)
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(20.dp))
            when (screen) {
                AppScreen.WATER -> WaterSettingsContent(water)
                AppScreen.POMODORO -> PomodoroSettingsContent(pomodoro)
            }
            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            FooterSection()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 外观：两套设置共用 */
@Composable
fun AppearanceSection(viewModel: WaterTrackerViewModel) {
    val context = LocalContext.current
    Text("外观", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0 to "跟随系统", 1 to "浅色", 2 to "暗色").forEach { (mode, label) ->
            FilterChip(
                selected = viewModel.themeMode == mode,
                onClick = { viewModel.updateTheme(context, mode) },
                shape = CircleShape,
                label = { Text(label) },
                leadingIcon = if (viewModel.themeMode == mode) {
                    { Icon(Icons.Default.Check, null) }
                } else null
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Text(
            "暗色模式使用纯黑",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = viewModel.oledBlack,
            onCheckedChange = { viewModel.updateOledBlack(context, it) }
        )
    }
}

/** 页脚：版本号 / 作者 / Powered by / GitHub，两套设置共用 */
@Composable
fun FooterSection() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            "v" + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))

        Text(
            "yuki39fufu",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))

        // 深色主题配浅色字标，浅色主题配深色字标
        val darkFooter = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Powered by ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Image(
                painter = painterResource(R.drawable.deepseek_logo),
                contentDescription = "DeepSeek",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(12.dp).width(57.dp)
            )
            Text(
                " & ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Image(
                painter = painterResource(
                    if (darkFooter) R.drawable.gemini_logo_dark else R.drawable.gemini_logo_light
                ),
                contentDescription = "Gemini",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(12.dp).width(53.dp)
            )
        }
        Spacer(Modifier.height(4.dp))

        val uriHandler = LocalUriHandler.current
        Text(
            GITHUB_URL,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri(GITHUB_URL) }
            }
        )
    }
}

/** 饮水设置 */
@Composable
fun WaterSettingsContent(viewModel: WaterTrackerViewModel) {
    val context = LocalContext.current
    val notificationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            viewModel.notificationsGranted = granted
            if (!granted) Toast.makeText(context, "没有拿到通知权限，请到系统设置里手动开启", Toast.LENGTH_LONG).show()
        }

    LaunchedEffect(Unit) { viewModel.refreshPermissionState(context) }

    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    var showCapacityDialog by rememberSaveable { mutableStateOf(false) }
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showCheckTimePicker by rememberSaveable { mutableStateOf(false) }
    var showPercentageDialog by rememberSaveable { mutableStateOf(false) }

    if (showGoalDialog) EditNumberDialog(
        title = "设定每日目标 (mL)",
        initialValue = viewModel.dailyGoal,
        onConfirm = { viewModel.updateDailyGoal(context, it); showGoalDialog = false },
        onDismiss = { showGoalDialog = false }
    )
    if (showWeightDialog) EditNumberDialog(
        title = "体重 (kg)",
        initialValue = viewModel.weightKg,
        onConfirm = { viewModel.updateWeight(context, it); showWeightDialog = false },
        onDismiss = { showWeightDialog = false }
    )
    if (showCapacityDialog) EditNumberDialog(
        title = "设定杯子容量 (mL)",
        initialValue = viewModel.cupCapacity,
        onConfirm = { viewModel.updateCupCapacity(context, it); showCapacityDialog = false },
        onDismiss = { showCapacityDialog = false }
    )
    if (showTimePicker) WaterTimePickerDialog(
        onDismiss = { showTimePicker = false },
        onConfirm = { h, m ->
            viewModel.addReminder(context, String.format("%02d:%02d", h, m))
            Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show()
            showTimePicker = false
        }
    )
    if (showCheckTimePicker) WaterTimePickerDialog(
        onDismiss = { showCheckTimePicker = false },
        onConfirm = { h, m ->
            viewModel.updateCheckTime(context, String.format("%02d:%02d", h, m))
            Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show()
            showCheckTimePicker = false
        }
    )
    if (showPercentageDialog) EditPercentageDialog(
        initialValue = viewModel.checkPercentage,
        onConfirm = { viewModel.updateCheckPercentage(context, it); showPercentageDialog = false },
        onDismiss = { showPercentageDialog = false }
    )

    // 与「外观」同色的分区标题
    Text("饮水设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))

    // 每日目标
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("每日目标", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${viewModel.dailyGoal} mL",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = if (viewModel.manualGoal) {
                    Modifier.clip(RoundedCornerShape(percent = 50))
                        .clickable { showGoalDialog = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                }
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text(
                "开启手动设置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = viewModel.manualGoal,
                onCheckedChange = { viewModel.updateManualGoal(context, it) }
            )
        }

        // 高度以手动模式为基准固定，切换模式时页面不会跳动
        Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            androidx.compose.animation.AnimatedContent(
                targetState = viewModel.manualGoal,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(200)) { it / 4 }) togetherWith
                        (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                            androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(140)) { -it / 4 })
                },
                label = "goal-mode"
            ) { manual ->
                if (manual) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = {
                            if (viewModel.dailyGoal > 500) viewModel.updateDailyGoal(context, viewModel.dailyGoal - 500)
                        }) { Text("-500") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.updateDailyGoal(context, viewModel.dailyGoal + 500) }) { Text("+500") }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("体重", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${viewModel.weightKg} kg",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(percent = 50))
                                    .clickable { showWeightDialog = true }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            "建议饮水量 = ${viewModel.weightKg} × 35 − 1000 = ${suggestedIntake(viewModel.weightKg)} mL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Divider(Modifier.padding(top = 8.dp))

    // 水杯容量
    Spacer(Modifier.height(20.dp))
    SettingItem(title = "水杯容量", value = "${viewModel.cupCapacity} mL", onValueClick = { showCapacityDialog = true }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                if (viewModel.cupCapacity > 50) viewModel.updateCupCapacity(context, viewModel.cupCapacity - 50)
            }) { Text("-50") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.updateCupCapacity(context, viewModel.cupCapacity + 50) }) { Text("+50") }
        }
    }
    Divider(Modifier.padding(top = 16.dp))

    // 达标检查
    Spacer(Modifier.height(20.dp))
    SettingItem(title = "达标检查", value = viewModel.checkTime, onValueClick = { showCheckTimePicker = true }) {
        Text("每日此时检查饮水量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
    Divider(Modifier.padding(top = 16.dp))

    // 提醒阈值
    Spacer(Modifier.height(20.dp))
    SettingItem(title = "提醒阈值", value = "${viewModel.checkPercentage}%", onValueClick = { showPercentageDialog = true }) {
        Text("若未达到此百分比则发送提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
    Divider(Modifier.padding(top = 16.dp))

    // 提醒时间
    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("提醒时间", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
        ) { Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
    Spacer(Modifier.height(8.dp))
    if (viewModel.reminders.isEmpty()) {
        Text("暂无提醒", color = MaterialTheme.colorScheme.outline)
    }
    viewModel.reminders.forEach { time ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.removeReminder(context, time) }) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    // 权限
    Spacer(Modifier.height(20.dp))
    Text("权限", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    PermissionItem(
        title = "通知权限",
        value = if (viewModel.notificationsGranted) "已开启" else "未开启（提醒无法显示）",
        isOn = viewModel.notificationsGranted,
        actionLabel = if (viewModel.notificationsGranted) "系统设置" else "去开启",
        onAction = {
            val asked = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATION_ASKED, false)
            if (!viewModel.notificationsGranted && !asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings(context)
            }
        }
    )
    PermissionItem(
        title = "电池优化",
        value = if (viewModel.ignoringBatteryOptimizations) "已忽略（可后台运行）" else "未忽略（可能延迟提醒）",
        isOn = viewModel.ignoringBatteryOptimizations,
        actionLabel = if (viewModel.ignoringBatteryOptimizations) "查看" else "去设置",
        onAction = {
            if (viewModel.ignoringBatteryOptimizations) {
                startSystemScreen(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } else {
                requestIgnoreBatteryOptimizations(context)
            }
        }
    )
    PermissionItem(
        title = "精确闹钟",
        value = if (viewModel.exactAlarmsAllowed) "已允许" else "未允许（提醒可能晚几分钟）",
        isOn = viewModel.exactAlarmsAllowed,
        actionLabel = "去设置",
        onAction = { requestExactAlarmPermission(context) }
    )
}
