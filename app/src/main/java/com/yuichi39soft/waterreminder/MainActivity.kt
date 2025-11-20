package com.yuichi39soft.waterreminder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 开启 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        createNotificationChannel()
        setContent {
            val viewModel = remember { WaterTrackerViewModel() }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                viewModel.loadSettings(context)
                checkExactAlarmPermission(context)
            }

            AppTheme(themeMode = viewModel.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WaterTrackerApp(viewModel)
                }
            }
        }
    }

    private fun checkExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "为了保证提醒准确，请授予“设置闹钟和提醒”权限", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel("water_channel_id", "喝水提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "提醒您按时喝水"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)

            val silentChannel = NotificationChannel("water_silent_channel_id", "状态更新", NotificationManager.IMPORTANCE_LOW).apply {
                description = "设置成功提示"
            }
            notificationManager.createNotificationChannel(silentChannel)
        }
    }
}

@Composable
fun AppTheme(themeMode: Int, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

class WaterNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.yuichi39soft.waterreminder.SHOW"
        const val ACTION_SNOOZE_FIXED = "com.yuichi39soft.waterreminder.SNOOZE_FIXED"
        const val ACTION_SNOOZE_CUSTOM = "com.yuichi39soft.waterreminder.SNOOZE_CUSTOM"
        const val ACTION_CHECK_INTAKE = "com.yuichi39soft.waterreminder.CHECK_INTAKE"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        val currentIntake = prefs.getInt("total_intake", 0)
        val dailyGoal = prefs.getInt("daily_goal", 2000)
        val checkPercentage = prefs.getInt("check_percentage", 50)
        val isGoalReached = currentIntake >= dailyGoal

        when (intent.action) {
            ACTION_CHECK_INTAKE -> {
                if (currentIntake < dailyGoal * (checkPercentage / 100.0)) {
                    sendLowIntakeNotification(context, currentIntake, checkPercentage)
                }
            }
            ACTION_SNOOZE_FIXED -> {
                scheduleSnooze(context, 5)
                updateNotification(context, "5分钟后提醒", silent = true)
            }
            ACTION_SNOOZE_CUSTOM -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val inputStr = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()
                val minutes = inputStr?.toIntOrNull()
                if (minutes != null && minutes > 0) {
                    scheduleSnooze(context, minutes)
                    updateNotification(context, "${minutes}分钟后提醒", silent = true)
                } else {
                    Toast.makeText(context, "数字无效", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                if (isGoalReached) return
                showNotification(context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLowIntakeNotification(context: Context, current: Int, percentage: Int) {
        val notification = NotificationCompat.Builder(context, "water_channel_id")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("今日饮水不足")
            .setContentText("今天只喝了 $current mL，还不到目标的 $percentage% 哦，加油补水！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification) } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(context: Context, text: String, silent: Boolean) {
        val channelId = if (silent) "water_silent_channel_id" else "water_channel_id"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("设置成功")
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(3000L)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) } catch (e: Exception) {}
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleSnooze(context: Context, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply { action = ACTION_SHOW_NOTIFICATION }
        val pendingIntent = PendingIntent.getBroadcast(context, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val triggerTime = System.currentTimeMillis() + minutes * 60 * 1000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context) {
        val mainIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val mainPendingIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val snoozeFixedIntent = Intent(context, WaterNotificationReceiver::class.java).apply { action = ACTION_SNOOZE_FIXED }
        val snoozeFixedPendingIntent = PendingIntent.getBroadcast(context, 1, snoozeFixedIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("分钟数").build()
        val snoozeCustomIntent = Intent(context, WaterNotificationReceiver::class.java).apply { action = ACTION_SNOOZE_CUSTOM }
        val snoozeCustomPendingIntent = PendingIntent.getBroadcast(
            context, 2, snoozeCustomIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val customAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_edit, "自定义", snoozeCustomPendingIntent).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, "water_channel_id")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("该喝水啦！")
            .setContentText("保持水分充足。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "5分钟后", snoozeFixedPendingIntent)
            .addAction(customAction)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) } catch (e: Exception) {}
    }
}

class WaterTrackerViewModel {
    var totalIntake by mutableIntStateOf(0)
    var dailyGoal by mutableIntStateOf(2000)
    var cupCapacity by mutableIntStateOf(300)
    var themeMode by mutableIntStateOf(0)
    var checkTime by mutableStateOf("20:00")
    var checkPercentage by mutableIntStateOf(50)
    var showCelebration by mutableStateOf(false)

    val reminders = mutableStateListOf("09:00", "11:00", "14:00", "16:00")

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()

        if (prefs.getString("last_date", "") != today) {
            totalIntake = 0
            saveIntake(context, 0)
            prefs.edit().putString("last_date", today).apply()
        } else {
            totalIntake = prefs.getInt("total_intake", 0)
        }

        dailyGoal = prefs.getInt("daily_goal", 2000)
        cupCapacity = prefs.getInt("cup_capacity", 300)
        themeMode = prefs.getInt("theme_mode", 0)
        checkTime = prefs.getString("check_time", "20:00") ?: "20:00"
        checkPercentage = prefs.getInt("check_percentage", 50)

        scheduleIntakeCheck(context, checkTime)
    }

    private fun saveIntake(context: Context, intake: Int) {
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
            .edit().putInt("total_intake", intake).apply()
    }

    fun updateTheme(context: Context, mode: Int) {
        themeMode = mode
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putInt("theme_mode", mode).apply()
    }

    fun updateCheckTime(context: Context, time: String) {
        checkTime = time
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putString("check_time", time).apply()
        scheduleIntakeCheck(context, time)
    }

    fun updateCheckPercentage(context: Context, percentage: Int) {
        checkPercentage = percentage
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putInt("check_percentage", percentage).apply()
    }

    fun addWater(context: Context, amount: Int) {
        val wasReached = totalIntake >= dailyGoal
        totalIntake += amount
        saveIntake(context, totalIntake)
        if (!wasReached && totalIntake >= dailyGoal) {
            showCelebration = true
        }
    }

    fun resetDaily(context: Context) {
        totalIntake = 0
        saveIntake(context, 0)
    }

    fun updateDailyGoal(context: Context, goal: Int) {
        dailyGoal = goal
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putInt("daily_goal", goal).apply()
    }

    fun updateCupCapacity(context: Context, capacity: Int) {
        cupCapacity = capacity
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putInt("cup_capacity", capacity).apply()
    }

    fun addReminder(context: Context, time: String) {
        if (!reminders.contains(time)) {
            reminders.add(time)
            reminders.sort()
            scheduleAlarm(context, time)
        }
    }

    fun removeReminder(context: Context, time: String) {
        reminders.remove(time)
        cancelAlarm(context, time)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleIntakeCheck(context: Context, timeStr: String) {
        val parts = timeStr.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply { action = WaterNotificationReceiver.ACTION_CHECK_INTAKE }
        val pendingIntent = PendingIntent.getBroadcast(context, 2001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DATE, 1)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {}
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(context: Context, timeStr: String) {
        val parts = timeStr.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply { action = WaterNotificationReceiver.ACTION_SHOW_NOTIFICATION }
        val pendingIntent = PendingIntent.getBroadcast(context, hour * 100 + minute, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DATE, 1)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "请开启精确闹钟权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm(context: Context, timeStr: String) {
        val parts = timeStr.split(":")
        val reqCode = parts[0].toInt() * 100 + parts[1].toInt()
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply { action = WaterNotificationReceiver.ACTION_SHOW_NOTIFICATION }
        val pendingIntent = PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        }
    }
}

@Composable
fun CelebrationEffect(
    visible: Boolean,
    onFinished: () -> Unit
) {
    if (visible) {
        val density = LocalDensity.current
        val particles = remember {
            List(100) {
                Particle(
                    color = listOf(
                        Color(0xFFFFD700), Color(0xFFFF6B6B), Color(0xFF4ECDC4),
                        Color(0xFF45B7D1), Color(0xFFFF9F43)
                    ).random(),
                    angle = Random.nextDouble(0.0, 360.0),
                    speed = Random.nextDouble(10.0, 30.0) * density.density
                )
            }
        }
        val animatable = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            animatable.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 1500, easing = LinearEasing))
            onFinished()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val progress = animatable.value
            particles.forEach { particle ->
                val rad = Math.toRadians(particle.angle)
                val dist = particle.speed * (progress * 25)
                val gravity = 1000f * (progress * progress)
                val x = center.x + (cos(rad) * dist).toFloat()
                val y = center.y + (sin(rad) * dist).toFloat() + gravity - 400f
                val alpha = (1f - progress).coerceIn(0f, 1f)
                drawCircle(color = particle.color.copy(alpha = alpha), radius = 8.dp.toPx() * (1f - progress), center = Offset(x, y))
            }
        }
    }
}

data class Particle(val color: Color, val angle: Double, val speed: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTrackerApp(viewModel: WaterTrackerViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) { Icon(Icons.Default.Settings, "设置") }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重置今日", tint = MaterialTheme.colorScheme.outline)
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("今日饮水", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${viewModel.totalIntake}",
                        fontSize = 80.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (viewModel.totalIntake >= viewModel.dailyGoal) {
                        Text(
                            "已达标",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "/ ${viewModel.dailyGoal} mL",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TrapezoidCup(viewModel.cupCapacity) { viewModel.addWater(context, it) }
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    dragHandle = null
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        BottomSheetDefaults.DragHandle()
                        SettingsContent(viewModel)
                    }
                }
            }
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("重置今日数据") },
                    text = { Text("确定要将今日的饮水记录清零吗？此操作无法撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resetDaily(context)
                                showResetDialog = false
                            },
                            // 【修改点】添加这一行，将按钮文字设为红色（使用主题的 Error 色）
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("重置")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text("取消") }
                    }
                )
            }
        }
        CelebrationEffect(
            visible = viewModel.showCelebration,
            onFinished = { viewModel.showCelebration = false }
        )
    }
}

@Composable
fun TrapezoidCup(cupCapacity: Int, onDrinkConfirmed: (Int) -> Unit) {
    var waterLevel by remember { mutableFloatStateOf(0f) }
    var currentVolume by remember { mutableIntStateOf(0) }
    val waterColor = Color(0xFF9AFCFF)
    val outlineColor = MaterialTheme.colorScheme.outline
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Box(
        modifier = Modifier.size(220.dp, 340.dp).pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = { if (currentVolume > 0) { onDrinkConfirmed(currentVolume); waterLevel = 0f; currentVolume = 0 } }) { change, _ ->
                change.consume(); val newLevel = (1f - (change.position.y / size.height)).coerceIn(0f, 1f); waterLevel = newLevel; currentVolume = (newLevel * cupCapacity).roundToInt()
            }
        }, contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height; val w = size.width; val waterH = h * waterLevel
            val cupPath = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w * 0.85f, h); lineTo(w * 0.15f, h); close() }
            clipPath(cupPath) { drawRect(color = containerColor); drawRect(color = waterColor, topLeft = Offset(0f, h - waterH), size = Size(w, waterH)) }
            drawPath(cupPath, outlineColor, style = Stroke(width = 4.dp.toPx()))
        }
        if (currentVolume > 0) Text("+$currentVolume", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(bottom = 100.dp))
    }
}

@Composable
fun EditNumberDialog(title: String, initialValue: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value = text, onValueChange = { if (it.all { c -> c.isDigit() }) text = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, label = { Text("输入数值") }) }, confirmButton = { TextButton(onClick = { val num = text.toIntOrNull(); if (num != null && num > 0) onConfirm(num) }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

// --- 【新增】百分比编辑弹窗（含滑条）---
@Composable
fun EditPercentageDialog(
    initialValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 滑条的值 (Float)
    var sliderPosition by remember { mutableFloatStateOf(initialValue.toFloat()) }
    // 输入框的值 (String)
    var textValue by remember { mutableStateOf(initialValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置提醒阈值") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 数字输入框
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            textValue = it
                            val num = it.toIntOrNull()
                            if (num != null) {
                                // 限制滑条范围 1-99
                                sliderPosition = num.coerceIn(1, 99).toFloat()
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("百分比 (%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 滑条
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        // 虽然滑条是连续的，但我们在这里实时取整更新文字，保持整数逻辑
                        textValue = it.roundToInt().toString()
                    },
                    valueRange = 1f..99f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val num = textValue.toIntOrNull()
                if (num != null && num in 1..99) {
                    onConfirm(num)
                } else {
                    // 如果输入无效，使用滑条的值
                    onConfirm(sliderPosition.roundToInt())
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTimePickerDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val currentTime = Calendar.getInstance()
    val timeState = rememberTimePickerState(initialHour = currentTime.get(Calendar.HOUR_OF_DAY), initialMinute = currentTime.get(Calendar.MINUTE), is24Hour = true)
    AlertDialog(onDismissRequest = onDismiss, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("确定") } }, text = { TimePicker(state = timeState) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(viewModel: WaterTrackerViewModel) {
    val context = LocalContext.current
    var showGoalDialog by remember { mutableStateOf(false) }
    var showCapacityDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCheckTimePicker by remember { mutableStateOf(false) }
    // 【新增】百分比弹窗状态
    var showPercentageDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) EditNumberDialog(title = "设定每日目标 (mL)", initialValue = viewModel.dailyGoal, onConfirm = { viewModel.updateDailyGoal(context, it); showGoalDialog = false }, onDismiss = { showGoalDialog = false })
    if (showCapacityDialog) EditNumberDialog(title = "设定杯子容量 (mL)", initialValue = viewModel.cupCapacity, onConfirm = { viewModel.updateCupCapacity(context, it); showCapacityDialog = false }, onDismiss = { showCapacityDialog = false })
    if (showTimePicker) WaterTimePickerDialog(onDismiss = { showTimePicker = false }, onConfirm = { h, m -> viewModel.addReminder(context, String.format("%02d:%02d", h, m)); Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show(); showTimePicker = false })
    if (showCheckTimePicker) WaterTimePickerDialog(onDismiss = { showCheckTimePicker = false }, onConfirm = { h, m -> viewModel.updateCheckTime(context, String.format("%02d:%02d", h, m)); Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show(); showCheckTimePicker = false })

    // 【新增】显示百分比设置弹窗 (使用新的 EditPercentageDialog)
    if (showPercentageDialog) {
        EditPercentageDialog(
            initialValue = viewModel.checkPercentage,
            onConfirm = {
                viewModel.updateCheckPercentage(context, it)
                showPercentageDialog = false
            },
            onDismiss = { showPercentageDialog = false }
        )
    }

    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Text("外观", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, label) -> FilterChip(selected = viewModel.themeMode == mode, onClick = { viewModel.updateTheme(context, mode) }, label = { Text(label) }, leadingIcon = if (viewModel.themeMode == mode) { { Icon(Icons.Default.Check, null) } } else null) } }
            Divider(Modifier.padding(top = 20.dp))
        }
        item {
            SettingItem(title = "每日目标", value = "${viewModel.dailyGoal} mL", onValueClick = { showGoalDialog = true }) {
                Row { Button(onClick = { if (viewModel.dailyGoal > 500) viewModel.updateDailyGoal(context, viewModel.dailyGoal - 500) }) { Text("-500") }; Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.updateDailyGoal(context, viewModel.dailyGoal + 500) }) { Text("+500") } }
            }
            Divider(Modifier.padding(top = 16.dp))
        }
        item {
            SettingItem(title = "水杯容量", value = "${viewModel.cupCapacity} mL", onValueClick = { showCapacityDialog = true }) {
                Row { Button(onClick = { if (viewModel.cupCapacity > 50) viewModel.updateCupCapacity(context, viewModel.cupCapacity - 50) }) { Text("-50") }; Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.updateCupCapacity(context, viewModel.cupCapacity + 50) }) { Text("+50") } }
            }
            Divider(Modifier.padding(top = 16.dp))
        }
        item {
            SettingItem(title = "达标检查", value = viewModel.checkTime, onValueClick = { showCheckTimePicker = true }) { Text("每日此时检查饮水量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            Divider(Modifier.padding(top = 16.dp))
        }

        // 【新增】设置项：提醒阈值
        item {
            SettingItem(title = "提醒阈值", value = "${viewModel.checkPercentage}%", onValueClick = { showPercentageDialog = true }) {
                Text("若未达到此百分比则发送提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Divider(Modifier.padding(top = 16.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("提醒时间", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { showTimePicker = true }, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))) { Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (viewModel.reminders.isEmpty()) item { Text("暂无提醒", color = MaterialTheme.colorScheme.outline) }
        items(items = viewModel.reminders, key = { it }) { time ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.removeReminder(context, time) }) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "v1.1.1",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "yuichi39",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "Ciallo～(∠・ω< )⌒★",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Powered by Gemini",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    GeminiIcon(
                        modifier = Modifier.size(12.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}


@Composable
fun SettingItem(title: String, value: String, onValueClick: (() -> Unit)? = null, controls: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).then(if (onValueClick != null) { Modifier.clickable { onValueClick() }.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 4.dp) } else Modifier))
        }
        Spacer(Modifier.height(8.dp)); controls()
    }
}

// --- 【新增】纯代码绘制的 Gemini 图标 ---
@Composable
fun GeminiIcon(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2

        val path = Path().apply {
            // 绘制四角星形状
            moveTo(cx, 0f) // 顶点
            // 上右象限内凹曲线
            quadraticBezierTo(cx, cy, w, cy)
            // 下右象限内凹曲线
            quadraticBezierTo(cx, cy, cx, h)
            // 下左象限内凹曲线
            quadraticBezierTo(cx, cy, 0f, cy)
            // 上左象限内凹曲线
            quadraticBezierTo(cx, cy, cx, 0f)
            close()
        }
        drawPath(path, color)
    }
}
