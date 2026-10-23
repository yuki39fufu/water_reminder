package com.yuichi39soft.waterreminder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import com.yuichi39soft.waterreminder.ui.theme.AppTypography

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: WaterTrackerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 开启 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        createNotificationChannel()
        viewModel = WaterTrackerViewModel()

        setContent {
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                viewModel.loadSettings(context)
            }

            AppTheme(themeMode = viewModel.themeMode, oledBlack = viewModel.oledBlack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WaterTrackerApp(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页返回时同步权限状态
        if (::viewModel.isInitialized) viewModel.refreshPermissionState(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 通道一旦创建，声音/振动/锁屏可见性就无法再修改，所以换新 ID 重建
            notificationManager.deleteNotificationChannel("water_channel_id")
            notificationManager.deleteNotificationChannel("water_silent_channel_id")

            val channel = NotificationChannel(CHANNEL_REMINDER, "喝水提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "提醒您按时喝水"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
                setShowBadge(true)
                // 默认在锁屏上完整显示；IMPORTANCE_HIGH 同时保证横幅弹出
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)

            val silentChannel = NotificationChannel(CHANNEL_STATUS, "状态更新", NotificationManager.IMPORTANCE_LOW).apply {
                description = "设置成功提示"
            }
            notificationManager.createNotificationChannel(silentChannel)
        }
    }
}

@Composable
fun AppTheme(themeMode: Int, oledBlack: Boolean, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    // 0 跟随系统，1 浅色，2 暗色
    val useDarkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    // 纯黑只在暗色生效时才有意义
    val pureBlack = useDarkTheme && oledBlack
    val context = LocalContext.current

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) {
                val dynamic = dynamicDarkColorScheme(context)
                if (pureBlack) dynamic.asOledBlack() else dynamic
            } else {
                dynamicLightColorScheme(context)
            }
        }
        pureBlack -> darkColorScheme().asOledBlack()
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

    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}

/**
 * OLED 纯黑：把背景与各类容器色压到纯黑（#000000），
 * 只保留稍亮的卡片层级和足够对比度的前景色，省电且不刺眼。
 */
private fun ColorScheme.asOledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF060606),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFFAFAFAF),
    outline = Color(0xFF525252),
    outlineVariant = Color(0xFF2C2C2C),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF151515)
)

private const val PREFS_NAME = "water_prefs"
private const val KEY_REMINDERS = "reminders"
private const val KEY_NOTIFICATION_ASKED = "notification_asked"
private const val KEY_BATTERY_ASKED = "battery_asked"
private const val KEY_MANUAL_GOAL = "manual_goal_enabled"
private const val KEY_WEIGHT_KG = "weight_kg"
private const val KEY_OLED_BLACK = "oled_black"

private const val CHANNEL_REMINDER = "water_reminder_v2"
private const val CHANNEL_STATUS = "water_status_v2"

private const val DEFAULT_WEIGHT_KG = 65
private const val GITHUB_URL = "https://github.com/yuki39fufu/water_reminder"

private val DEFAULT_REMINDERS = listOf("09:00", "11:00", "14:00", "16:00")

/** 建议饮水量 = 体重(kg) × 35 - 1000，下限 500 mL，避免体重过小算出离谱的值 */
fun suggestedIntake(weightKg: Int): Int = (weightKg * 35 - 1000).coerceIn(500, 6000)

/** 通知权限：Android 13 起是运行时权限，没授予时 notify() 会被系统静默丢弃 */
fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/** 是否已被系统排除电池优化，等同于“允许后台运行” */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/** 拉起系统的“忽略电池优化”确认框；部分 ROM 没有这个页面，退回到优化列表 */
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (isIgnoringBatteryOptimizations(context)) return
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    if (!startSystemScreen(context, direct)) {
        startSystemScreen(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return alarmManager.canScheduleExactAlarms()
}

/** Android 14 起精确闹钟默认不再预授权，需要用户手动打开 */
fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val direct = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    )
    if (!startSystemScreen(context, direct)) {
        startSystemScreen(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
}

fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    startSystemScreen(context, intent)
}

private fun startSystemScreen(context: Context, intent: Intent): Boolean {
    return try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * 闹钟统一入口。
 * AlarmManager 的闹钟都是一次性的：响过之后必须自己排下一次，否则提醒只触发一次。
 * 所有“算下一次触发时间”的逻辑都收在这里，避免调用方各写一份。
 */
object AlarmScheduler {
    const val EXTRA_TIME = "extra_time"
    private const val REMINDER_REQUEST_BASE = 10_000
    private const val CHECK_REQUEST_CODE = 2001
    private const val SNOOZE_REQUEST_BASE = 30_000
    private const val SHOW_APP_REQUEST_CODE = 9

    fun nextTriggerMillis(timeStr: String): Long {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DATE, 1)
        return target.timeInMillis
    }

    private fun requestCodeOf(timeStr: String): Int {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return REMINDER_REQUEST_BASE + hour * 100 + minute
    }

    fun scheduleReminder(context: Context, timeStr: String) {
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply {
            action = WaterNotificationReceiver.ACTION_SHOW_NOTIFICATION
            putExtra(EXTRA_TIME, timeStr)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeOf(timeStr),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setAlarm(context, nextTriggerMillis(timeStr), pendingIntent)
    }

    fun cancelReminder(context: Context, timeStr: String) {
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply {
            action = WaterNotificationReceiver.ACTION_SHOW_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeOf(timeStr),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pendingIntent)
        }
    }

    fun scheduleDailyCheck(context: Context, timeStr: String) {
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply {
            action = WaterNotificationReceiver.ACTION_CHECK_INTAKE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            CHECK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setAlarm(context, nextTriggerMillis(timeStr), pendingIntent)
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        val intent = Intent(context, WaterNotificationReceiver::class.java).apply {
            action = WaterNotificationReceiver.ACTION_SHOW_NOTIFICATION
            // 推迟触发不参与“每日循环”，这里留空避免被当成某个固定时间点
            putExtra(EXTRA_TIME, "")
        }
        val requestCode = SNOOZE_REQUEST_BASE + (System.currentTimeMillis() % 1000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setAlarm(context, System.currentTimeMillis() + minutes * 60_000L, pendingIntent)
    }

    private fun setAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        fun scheduleWithAlarmClock() {
            // setAlarmClock 是系统里优先级最高的闹钟类型：Doze 和多数厂商的省电策略都不会拦截，
            // 代价是状态栏会出现一个闹钟图标。
            val showIntent = PendingIntent.getActivity(
                context,
                SHOW_APP_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pendingIntent)
        }

        try {
            if (canScheduleExactAlarms(context)) {
                scheduleWithAlarmClock()
            } else {
                // 没有精确闹钟权限时降级：仍会提醒，但系统可能延迟几分钟
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (ignored: Exception) {
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIntake = prefs.getInt("total_intake", 0)
        val dailyGoal = prefs.getInt("daily_goal", 2000)
        val checkPercentage = prefs.getInt("check_percentage", 50)
        val isGoalReached = currentIntake >= dailyGoal

        when (intent.action) {
            ACTION_CHECK_INTAKE -> {
                // 先把明天的检查排上，再决定要不要提醒（一天一次，必须自己续期）
                val checkTime = prefs.getString("check_time", "20:00") ?: "20:00"
                AlarmScheduler.scheduleDailyCheck(context, checkTime)
                if (currentIntake < dailyGoal * (checkPercentage / 100.0) && hasNotificationPermission(context)) {
                    sendLowIntakeNotification(context, currentIntake, checkPercentage)
                }
            }
            ACTION_SNOOZE_FIXED -> {
                AlarmScheduler.scheduleSnooze(context, 5)
                if (hasNotificationPermission(context)) updateNotification(context, "5分钟后提醒", silent = true)
            }
            ACTION_SNOOZE_CUSTOM -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val inputStr = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()
                val minutes = inputStr?.toIntOrNull()
                if (minutes != null && minutes > 0) {
                    AlarmScheduler.scheduleSnooze(context, minutes)
                    if (hasNotificationPermission(context)) updateNotification(context, "${minutes}分钟后提醒", silent = true)
                } else {
                    Toast.makeText(context, "数字无效", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // 关键修复：闹钟是一次性的，响完必须把下一次排上，否则只会响一天
                val reminderTime = intent.getStringExtra(AlarmScheduler.EXTRA_TIME)
                if (!reminderTime.isNullOrBlank()) AlarmScheduler.scheduleReminder(context, reminderTime)
                if (isGoalReached || !hasNotificationPermission(context)) return
                showNotification(context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLowIntakeNotification(context: Context, current: Int, percentage: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("今日饮水不足")
            .setContentText("今天只喝了 $current mL，还不到目标的 $percentage% 哦，加油补水！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification) } catch (e: Exception) {}
    }

    /** 点击通知回到应用主界面 */
    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(context: Context, text: String, silent: Boolean) {
        val channelId = if (silent) CHANNEL_STATUS else CHANNEL_REMINDER
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("设置成功")
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(3000L)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context) {
        val mainPendingIntent = openAppPendingIntent(context)

        val snoozeFixedIntent = Intent(context, WaterNotificationReceiver::class.java).apply { action = ACTION_SNOOZE_FIXED }
        val snoozeFixedPendingIntent = PendingIntent.getBroadcast(context, 1, snoozeFixedIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("分钟数").build()
        val snoozeCustomIntent = Intent(context, WaterNotificationReceiver::class.java).apply { action = ACTION_SNOOZE_CUSTOM }
        val snoozeCustomPendingIntent = PendingIntent.getBroadcast(
            context, 2, snoozeCustomIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val customAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_edit, "自定义", snoozeCustomPendingIntent).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("该喝水啦！")
            .setContentText("保持水分充足。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
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
    // 暗色模式下是否使用纯黑（OLED）
    var oledBlack by mutableStateOf(false)
    var checkTime by mutableStateOf("20:00")
    var checkPercentage by mutableIntStateOf(50)
    var showCelebration by mutableStateOf(false)

    // 每日目标：true = 手动设置，false = 按体重自动计算
    var manualGoal by mutableStateOf(true)
    var weightKg by mutableIntStateOf(DEFAULT_WEIGHT_KG)

    // 权限状态，供设置页实时展示
    var notificationsGranted by mutableStateOf(true)
    var ignoringBatteryOptimizations by mutableStateOf(true)
    var exactAlarmsAllowed by mutableStateOf(true)

    val reminders = mutableStateListOf<String>()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun refreshPermissionState(context: Context) {
        notificationsGranted = hasNotificationPermission(context)
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        exactAlarmsAllowed = canScheduleExactAlarms(context)
    }

    fun loadSettings(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now().toString()

        if (p.getString("last_date", "") != today) {
            totalIntake = 0
            saveIntake(context, 0)
            p.edit().putString("last_date", today).apply()
        } else {
            totalIntake = p.getInt("total_intake", 0)
        }

        dailyGoal = p.getInt("daily_goal", 2000)
        cupCapacity = p.getInt("cup_capacity", 300)
        themeMode = p.getInt("theme_mode", 0)
        oledBlack = p.getBoolean(KEY_OLED_BLACK, false)
        // 旧版本用 themeMode=3 表示纯黑，迁移为「暗色 + 纯黑开关」
        if (themeMode == 3) {
            themeMode = 2
            oledBlack = true
            p.edit().putInt("theme_mode", 2).putBoolean(KEY_OLED_BLACK, true).apply()
        }
        checkTime = p.getString("check_time", "20:00") ?: "20:00"
        checkPercentage = p.getInt("check_percentage", 50)

        manualGoal = p.getBoolean(KEY_MANUAL_GOAL, true)
        weightKg = p.getInt(KEY_WEIGHT_KG, DEFAULT_WEIGHT_KG)
        if (!manualGoal) {
            // 自动模式下，目标由体重推出，覆盖掉历史值
            dailyGoal = suggestedIntake(weightKg)
            p.edit().putInt("daily_goal", dailyGoal).apply()
        }

        // 提醒时间从本地恢复；老版本没存过就回落到默认值
        val savedReminders = p.getString(KEY_REMINDERS, null)
        val restored = if (savedReminders.isNullOrBlank()) {
            DEFAULT_REMINDERS
        } else {
            savedReminders.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        reminders.clear()
        reminders.addAll(restored.sorted())
        saveReminders(context)

        // 闹钟是一次性的，每次启动都重排一遍，保证重启或进程被杀后仍然能响
        reminders.forEach { AlarmScheduler.scheduleReminder(context, it) }
        AlarmScheduler.scheduleDailyCheck(context, checkTime)

        refreshPermissionState(context)
    }

    private fun saveIntake(context: Context, intake: Int) {
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
            .edit().putInt("total_intake", intake).apply()
    }

    fun updateTheme(context: Context, mode: Int) {
        themeMode = mode
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putInt("theme_mode", mode).apply()
    }

    fun updateOledBlack(context: Context, enabled: Boolean) {
        oledBlack = enabled
        prefs(context).edit().putBoolean(KEY_OLED_BLACK, enabled).apply()
    }

    fun updateCheckTime(context: Context, time: String) {
        checkTime = time
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putString("check_time", time).apply()
        AlarmScheduler.scheduleDailyCheck(context, time)
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

    /** 切换手动/自动；切到自动时立刻按公式重算一次 */
    fun updateManualGoal(context: Context, manual: Boolean) {
        manualGoal = manual
        prefs(context).edit().putBoolean(KEY_MANUAL_GOAL, manual).apply()
        if (!manual) updateDailyGoal(context, suggestedIntake(weightKg))
    }

    fun updateWeight(context: Context, weight: Int) {
        weightKg = weight.coerceIn(20, 300)
        prefs(context).edit().putInt(KEY_WEIGHT_KG, weightKg).apply()
        // 自动模式下体重一变，目标跟着变；手动模式只记体重，不动目标
        if (!manualGoal) updateDailyGoal(context, suggestedIntake(weightKg))
    }

    fun updateCupCapacity(context: Context, capacity: Int) {
        cupCapacity = capacity
        context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE).edit().putInt("cup_capacity", capacity).apply()
    }

    fun addReminder(context: Context, time: String) {
        if (!reminders.contains(time)) {
            reminders.add(time)
            reminders.sort()
            saveReminders(context)
            AlarmScheduler.scheduleReminder(context, time)
        }
    }

    fun removeReminder(context: Context, time: String) {
        reminders.remove(time)
        saveReminders(context)
        AlarmScheduler.cancelReminder(context, time)
    }

    private fun saveReminders(context: Context) {
        prefs(context).edit().putString(KEY_REMINDERS, reminders.joinToString(",")).apply()
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
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.notificationsGranted = granted
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        if (!granted) {
            Toast.makeText(context, "没有通知权限，提醒无法显示，可到系统设置里手动开启", Toast.LENGTH_LONG).show()
        } else if (!isIgnoringBatteryOptimizations(context) && !prefs.getBoolean(KEY_BATTERY_ASKED, false)) {
            showBatteryDialog = true
        }
    }

    // 首次进入的引导顺序：先要通知权限，再要后台运行白名单
    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState(context)
        val needNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)
        when {
            needNotification && !prefs.getBoolean(KEY_NOTIFICATION_ASKED, false) -> showNotificationDialog = true
            !isIgnoringBatteryOptimizations(context) && !prefs.getBoolean(KEY_BATTERY_ASKED, false) -> showBatteryDialog = true
        }
    }

    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text("开启喝水提醒") },
            text = { Text("提醒需要通知权限才能显示在通知栏。授权后应用才能按时提醒你喝水；如果之前拒绝过，也可以到系统设置里重新打开。") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotificationDialog = false
                    prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
                    if (!isIgnoringBatteryOptimizations(context)) showBatteryDialog = true
                }) { Text("暂不") }
            }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = {
                showBatteryDialog = false
                prefs.edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
            },
            title = { Text("允许后台运行") },
            text = { Text("系统为了省电会限制后台应用，闹钟可能被延迟甚至不触发。把本应用设为“不受电池优化限制”后，提醒才能准时。") },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    prefs.edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
                    requestIgnoreBatteryOptimizations(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    prefs.edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
                }) { Text("稍后") }
            }
        )
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showBottomSheet = true },
                    shape = CircleShape,
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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.notificationsGranted = granted
        if (!granted) Toast.makeText(context, "没有拿到通知权限，请到系统设置里手动开启", Toast.LENGTH_LONG).show()
    }

    // 每次打开设置页都重新读一次权限状态，从系统设置返回后能立刻反映
    LaunchedEffect(Unit) { viewModel.refreshPermissionState(context) }

    var showGoalDialog by remember { mutableStateOf(false) }
    var showCapacityDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCheckTimePicker by remember { mutableStateOf(false) }
    // 【新增】百分比弹窗状态
    var showPercentageDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) EditNumberDialog(title = "设定每日目标 (mL)", initialValue = viewModel.dailyGoal, onConfirm = { viewModel.updateDailyGoal(context, it); showGoalDialog = false }, onDismiss = { showGoalDialog = false })
    if (showWeightDialog) EditNumberDialog(title = "体重 (kg)", initialValue = viewModel.weightKg, onConfirm = { viewModel.updateWeight(context, it); showWeightDialog = false }, onDismiss = { showWeightDialog = false })
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
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0 to "跟随系统", 1 to "浅色", 2 to "暗色").forEach { (mode, label) -> FilterChip(selected = viewModel.themeMode == mode, onClick = { viewModel.updateTheme(context, mode) }, shape = CircleShape, label = { Text(label) }, leadingIcon = if (viewModel.themeMode == mode) { { Icon(Icons.Default.Check, null) } } else null) } }
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
            Divider(Modifier.padding(top = 20.dp))
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("每日目标", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${viewModel.dailyGoal} mL",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = if (viewModel.manualGoal) {
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { showGoalDialog = true }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        } else {
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        }
                    )
                }

                // 手动设置开关：开 = 自己填，关 = 按体重自动算
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
                    AnimatedContent(
                        targetState = viewModel.manualGoal,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }) togetherWith
                                (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 4 })
                        },
                        label = "goal-mode"
                    ) { manual ->
                        if (manual) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(onClick = { if (viewModel.dailyGoal > 500) viewModel.updateDailyGoal(context, viewModel.dailyGoal - 500) }) { Text("-500") }
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
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
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
            Divider(Modifier.padding(top = 16.dp))
        }
        item {
            SettingItem(title = "水杯容量", value = "${viewModel.cupCapacity} mL", onValueClick = { showCapacityDialog = true }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { if (viewModel.cupCapacity > 50) viewModel.updateCupCapacity(context, viewModel.cupCapacity - 50) }) { Text("-50") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.updateCupCapacity(context, viewModel.cupCapacity + 50) }) { Text("+50") }
                }
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
                IconButton(onClick = { showTimePicker = true }, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)) { Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (viewModel.reminders.isEmpty()) item { Text("暂无提醒", color = MaterialTheme.colorScheme.outline) }
        items(items = viewModel.reminders, key = { it }) { time ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(percent = 50)).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.removeReminder(context, time) }) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Text("权限", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            PermissionItem(
                title = "通知权限",
                value = if (viewModel.notificationsGranted) "已开启" else "未开启（提醒无法显示）",
                isOn = viewModel.notificationsGranted,
                actionLabel = if (viewModel.notificationsGranted) "系统设置" else "去开启",
                onAction = {
                    // 没被问过就走系统弹窗；已经拒绝过系统不会再弹，直接跳应用通知设置
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
            Divider(Modifier.padding(top = 16.dp))
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "v" + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "yuki39fufu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 深色主题配浅色字标，浅色主题配深色字标
                val darkFooter = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Powered by ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    // 官方 SVG 转换而来，按 12dp 高度等比缩放
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
                Spacer(modifier = Modifier.height(4.dp))

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

@Composable
fun PermissionItem(title: String, value: String, isOn: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = if (isOn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** 重启或应用更新后重新排闹钟：AlarmManager 里的闹钟会随重启一起丢失 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val accepted = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        if (!accepted) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REMINDERS, null)
        val times = if (saved.isNullOrBlank()) {
            DEFAULT_REMINDERS
        } else {
            saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        times.forEach { AlarmScheduler.scheduleReminder(context, it) }
        AlarmScheduler.scheduleDailyCheck(context, prefs.getString("check_time", "20:00") ?: "20:00")
    }
}
