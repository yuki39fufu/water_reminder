package com.yuichi39soft.waterreminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

enum class PomodoroPhase { FOCUS, BREAK }

/**
 * 番茄钟状态。
 * 计时以"结束时间戳"为准，锁屏 / 切后台再回来不会累计误差。
 */
class PomodoroViewModel(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var focusMinutes by mutableIntStateOf(prefs.getInt(KEY_POM_FOCUS, 25))
        private set
    var breakMinutes by mutableIntStateOf(prefs.getInt(KEY_POM_BREAK, 5))
        private set
    var rounds by mutableIntStateOf(prefs.getInt(KEY_POM_ROUNDS, 4))
        private set
    var ringEnabled by mutableStateOf(prefs.getBoolean(KEY_POM_RING, true))
        private set

    var phase by mutableStateOf(loadPhase())
        private set
    var currentRound by mutableIntStateOf(prefs.getInt(KEY_POM_ROUND, 1))
        private set
    var running by mutableStateOf(false)
        private set
    var remainingMillis by mutableLongStateOf(0L)
        private set

    /** 每完成一个阶段自增；UI 用它触发响铃 */
    var completionSignal by mutableIntStateOf(0)
        private set

    private var endAt = 0L

    init {
        restore()
    }

    private fun loadPhase(): PomodoroPhase =
        runCatching { PomodoroPhase.valueOf(prefs.getString(KEY_POM_PHASE, "FOCUS") ?: "FOCUS") }
            .getOrDefault(PomodoroPhase.FOCUS)

    private fun focusMs() = focusMinutes * 60_000L
    private fun breakMs() = breakMinutes * 60_000L
    private fun phaseMs() = if (phase == PomodoroPhase.FOCUS) focusMs() else breakMs()

    /** 打开应用时把状态从本地恢复，并对已经过点的阶段做追平 */
    private fun restore() {
        val savedRunning = prefs.getBoolean(KEY_POM_RUNNING, false)
        val savedEnd = prefs.getLong(KEY_POM_END_AT, 0L)
        if (savedRunning && savedEnd > 0L) {
            running = true
            endAt = savedEnd
            val now = System.currentTimeMillis()
            var guard = 0
            while (running && endAt <= now && guard++ < 64) {
                advanceFrom(endAt)
            }
            remainingMillis = if (running) (endAt - now).coerceAtLeast(0L) else phaseMs()
            persist()
            if (running) scheduleAlarm() else cancelAlarm()
        } else {
            running = false
            endAt = 0L
            remainingMillis = phaseMs()
        }
    }

    /** 从 endTime 这个时间点推进到下一个阶段 */
    private fun advanceFrom(endTime: Long) {
        if (phase == PomodoroPhase.FOCUS) {
            if (currentRound >= rounds) {
                running = false
                phase = PomodoroPhase.FOCUS
                currentRound = 1
                endAt = 0L
                remainingMillis = focusMs()
            } else {
                phase = PomodoroPhase.BREAK
                endAt = endTime + breakMs()
                remainingMillis = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            }
        } else {
            currentRound = (currentRound + 1).coerceAtMost(rounds)
            phase = PomodoroPhase.FOCUS
            endAt = endTime + focusMs()
            remainingMillis = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }

    private fun persist() {
        prefs.edit()
            .putBoolean(KEY_POM_RUNNING, running)
            .putLong(KEY_POM_END_AT, endAt)
            .putString(KEY_POM_PHASE, phase.name)
            .putInt(KEY_POM_ROUND, currentRound)
            .apply()
    }

    private fun scheduleAlarm() {
        if (!running || endAt <= 0L) return
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = Intent(appContext, PomodoroAlarmReceiver::class.java).apply {
            action = ACTION_POMODORO_ALARM
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            appContext,
            POMODORO_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching {
            if (canScheduleExactAlarms(appContext)) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endAt, pendingIntent)
            }
        }
    }

    private fun cancelAlarm() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = Intent(appContext, PomodoroAlarmReceiver::class.java).apply {
            action = ACTION_POMODORO_ALARM
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            appContext,
            POMODORO_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }

    fun updateFocusMinutes(value: Int) {
        focusMinutes = value.coerceIn(1, 180)
        prefs.edit().putInt(KEY_POM_FOCUS, focusMinutes).apply()
        if (!running && phase == PomodoroPhase.FOCUS) remainingMillis = focusMs()
    }

    fun updateBreakMinutes(value: Int) {
        breakMinutes = value.coerceIn(1, 60)
        prefs.edit().putInt(KEY_POM_BREAK, breakMinutes).apply()
        if (!running && phase == PomodoroPhase.BREAK) remainingMillis = breakMs()
    }

    fun updateRounds(value: Int) {
        rounds = value.coerceIn(1, 12)
        prefs.edit().putInt(KEY_POM_ROUNDS, rounds).apply()
        if (currentRound > rounds) currentRound = rounds
    }

    fun updateRingEnabled(enabled: Boolean) {
        ringEnabled = enabled
        prefs.edit().putBoolean(KEY_POM_RING, enabled).apply()
    }

    fun toggle() {
        if (running) pause() else start()
    }

    fun start() {
        if (remainingMillis <= 0L) remainingMillis = phaseMs()
        endAt = System.currentTimeMillis() + remainingMillis
        running = true
        persist()
        scheduleAlarm()
    }

    fun pause() {
        remainingMillis = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        running = false
        persist()
        cancelAlarm()
    }

    fun reset() {
        running = false
        phase = PomodoroPhase.FOCUS
        currentRound = 1
        remainingMillis = focusMs()
        endAt = 0L
        persist()
        cancelAlarm()
    }

    /** 返回刚刚结束的阶段（若有），由调用方负责响铃 */
    fun tick(now: Long): PomodoroPhase? {
        if (!running) return null
        if (now < endAt) {
            remainingMillis = endAt - now
            return null
        }
        val finished = phase
        completionSignal++
        advanceFrom(endAt)
        remainingMillis = if (running) (endAt - now).coerceAtLeast(0L) else phaseMs()
        persist()
        if (running) scheduleAlarm() else cancelAlarm()
        return finished
    }

    /** 当前这一轮已经过去的比例（专注 + 休息合起来算） */
    fun roundConsumedFraction(): Float {
        val focus = focusMs().toFloat()
        val total = focus + breakMs()
        val elapsed = if (phase == PomodoroPhase.FOCUS) {
            focus - remainingMillis
        } else {
            focus + (breakMs() - remainingMillis)
        }
        return (elapsed / total).coerceIn(0f, 1f)
    }
}

/** 阶段结束的闹钟：只负责响铃/通知，阶段推进由应用内的状态机负责 */
class PomodoroAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endAt = prefs.getLong(KEY_POM_END_AT, 0L)
        if (endAt <= 0L) return
        // 同一个结束点只响一次（应用内 tick 与闹钟可能同时到达）
        val fired = prefs.getLong(KEY_POM_FIRED_AT, 0L)
        if (fired == endAt) return
        prefs.edit().putLong(KEY_POM_FIRED_AT, endAt).apply()

        if (prefs.getBoolean(KEY_POM_RING, true)) {
            playPhaseChime(context)
        }
        val phaseText = prefs.getString(KEY_POM_PHASE, "FOCUS")
        notifyPhaseFinished(
            context,
            if (phaseText == PomodoroPhase.BREAK.name) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun PomodoroScreen(pomodoro: PomodoroViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showFocusDialog by remember { mutableStateOf(false) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var showRoundDialog by remember { mutableStateOf(false) }

    if (showFocusDialog) EditNumberDialog(
        title = "专注时长 (分钟)",
        initialValue = pomodoro.focusMinutes,
        onConfirm = { pomodoro.updateFocusMinutes(it); showFocusDialog = false },
        onDismiss = { showFocusDialog = false }
    )
    if (showBreakDialog) EditNumberDialog(
        title = "休息时长 (分钟)",
        initialValue = pomodoro.breakMinutes,
        onConfirm = { pomodoro.updateBreakMinutes(it); showBreakDialog = false },
        onDismiss = { showBreakDialog = false }
    )
    if (showRoundDialog) EditNumberDialog(
        title = "循环轮次",
        initialValue = pomodoro.rounds,
        onConfirm = { pomodoro.updateRounds(it); showRoundDialog = false },
        onDismiss = { showRoundDialog = false }
    )

    // 计时驱动 + 阶段结束响铃
    LaunchedEffect(pomodoro.running) {
        while (pomodoro.running) {
            delay(200)
            val finished = pomodoro.tick(System.currentTimeMillis())
            if (finished != null && pomodoro.ringEnabled) {
                playPhaseChime(context)
            }
            if (finished != null) notifyPhaseFinished(context, finished)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(14.dp))

        // 两行参数：标题 / 数值
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ParamLabel("专注时长", Modifier.weight(1f))
            ParamLabel("休息时长", Modifier.weight(1f))
            ParamLabel("轮次", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ParamValue("${pomodoro.focusMinutes} min", Modifier.weight(1f)) { showFocusDialog = true }
            ParamValue("${pomodoro.breakMinutes} min", Modifier.weight(1f)) { showBreakDialog = true }
            ParamValue("${pomodoro.currentRound} / ${pomodoro.rounds}", Modifier.weight(1f)) { showRoundDialog = true }
        }

        Spacer(Modifier.weight(1f))

        Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            PomodoroRing(
                focusFractionOfRound = pomodoro.focusMinutes.toFloat() /
                    (pomodoro.focusMinutes + pomodoro.breakMinutes).toFloat(),
                consumedFraction = pomodoro.roundConsumedFraction(),
                ringWidth = 22.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDuration(pomodoro.remainingMillis),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !pomodoro.running && pomodoro.remainingMillis <= 0L -> "已结束"
                        pomodoro.phase == PomodoroPhase.FOCUS -> "专注中"
                        else -> "休息中"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ParamLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ParamValue(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 环形条带：
 * - 底环（深色）+ 专注段（primary）+ 休息段（tertiary），从 12 点顺时针
 * - 随时间流逝，从 12 点开始"擦掉"，露出底环
 * - 内外两条描边 + 分界处一条同宽的分隔线（不与描边叠加）
 */
@Composable
fun PomodoroRing(
    focusFractionOfRound: Float,
    consumedFraction: Float,
    ringWidth: Dp,
    modifier: Modifier = Modifier
) {
    val focusColor = MaterialTheme.colorScheme.primary
    val breakColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = ringWidth.toPx()
        val outlineStroke = 2.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        val focusAngle = (360f * focusFractionOfRound).coerceIn(0f, 360f)
        val consumed = (360f * consumedFraction).coerceIn(0f, 360f)

        // 底环
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )

        // 专注段（12 点方向起）
        val focusSweep = (focusAngle - consumed).coerceAtLeast(0f)
        if (focusSweep > 0f) {
            drawArc(
                color = focusColor,
                startAngle = -90f + consumed,
                sweepAngle = focusSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
        }

        // 休息段
        val breakStart = maxOf(consumed, focusAngle)
        val breakSweep = 360f - breakStart
        if (breakSweep > 0f) {
            drawArc(
                color = breakColor,
                startAngle = -90f + breakStart,
                sweepAngle = breakSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
        }

        // 内外描边
        drawCircle(
            color = outlineColor,
            radius = radius + stroke / 2f,
            center = center,
            style = Stroke(width = outlineStroke)
        )
        drawCircle(
            color = outlineColor,
            radius = radius - stroke / 2f,
            center = center,
            style = Stroke(width = outlineStroke)
        )

        // 专注/休息分界：一条与描边同宽的线，避免两段各自描边叠加成 4dp
        if (focusAngle > 0.5f && focusAngle < 359.5f) {
            val angle = Math.toRadians((-90f + focusAngle).toDouble())
            val inner = radius - stroke / 2f
            val outer = radius + stroke / 2f
            drawLine(
                color = outlineColor,
                start = Offset(
                    center.x + inner * cos(angle).toFloat(),
                    center.y + inner * sin(angle).toFloat()
                ),
                end = Offset(
                    center.x + outer * cos(angle).toFloat(),
                    center.y + outer * sin(angle).toFloat()
                ),
                strokeWidth = outlineStroke,
                cap = StrokeCap.Butt
            )
        }
    }
}

/** 开始 / 暂停按钮（悬浮层里，位于设置按钮上方） */
@Composable
fun StartPauseButton(running: Boolean, onClick: () -> Unit) {
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            if (running) {
                val barWidth = size.width * 0.26f
                val gap = size.width * 0.18f
                val top = size.height * 0.16f
                val barHeight = size.height * 0.68f
                val radius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(size.width / 2f - gap / 2f - barWidth, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(size.width / 2f + gap / 2f, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
            } else {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.14f)
                    lineTo(size.width * 0.84f, size.height * 0.5f)
                    lineTo(size.width * 0.28f, size.height * 0.86f)
                    close()
                }
                drawPath(path, iconColor)
            }
        }
    }
}

/** 阶段结束响铃（系统默认通知音） */
fun playPhaseChime(context: Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    }
}

/** 阶段结束通知 */
fun notifyPhaseFinished(context: Context, finished: PomodoroPhase) {
    if (!hasNotificationPermission(context)) return
    val text = if (finished == PomodoroPhase.FOCUS) "专注结束，休息一下吧" else "休息结束，继续专注"
    val notification = NotificationCompat.Builder(context, CHANNEL_POMODORO)
        .setSmallIcon(android.R.drawable.ic_menu_recent_history)
        .setContentTitle("番茄钟")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .build()
    runCatching {
        NotificationManagerCompat.from(context).notify(2002, notification)
    }
}

/** 番茄钟设置 */
@Composable
fun PomodoroSettingsContent(pomodoro: PomodoroViewModel) {
    var showFocusDialog by remember { mutableStateOf(false) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var showRoundDialog by remember { mutableStateOf(false) }

    if (showFocusDialog) EditNumberDialog(
        title = "专注时长 (分钟)",
        initialValue = pomodoro.focusMinutes,
        onConfirm = { pomodoro.updateFocusMinutes(it); showFocusDialog = false },
        onDismiss = { showFocusDialog = false }
    )
    if (showBreakDialog) EditNumberDialog(
        title = "休息时长 (分钟)",
        initialValue = pomodoro.breakMinutes,
        onConfirm = { pomodoro.updateBreakMinutes(it); showBreakDialog = false },
        onDismiss = { showBreakDialog = false }
    )
    if (showRoundDialog) EditNumberDialog(
        title = "循环轮次",
        initialValue = pomodoro.rounds,
        onConfirm = { pomodoro.updateRounds(it); showRoundDialog = false },
        onDismiss = { showRoundDialog = false }
    )

    Text("番茄钟设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("倒计时结束时响铃", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = pomodoro.ringEnabled,
            onCheckedChange = { pomodoro.updateRingEnabled(it) }
        )
    }
    Divider(Modifier.padding(top = 16.dp))

    Spacer(Modifier.height(20.dp))
    SettingItem(title = "专注时长", value = "${pomodoro.focusMinutes} 分钟", onValueClick = { showFocusDialog = true }) {
        Text("每个专注阶段持续的时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
    Divider(Modifier.padding(top = 16.dp))

    Spacer(Modifier.height(20.dp))
    SettingItem(title = "休息时长", value = "${pomodoro.breakMinutes} 分钟", onValueClick = { showBreakDialog = true }) {
        Text("每个休息阶段持续的时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
    Divider(Modifier.padding(top = 16.dp))

    Spacer(Modifier.height(20.dp))
    SettingItem(title = "循环轮次", value = "${pomodoro.rounds} 轮", onValueClick = { showRoundDialog = true }) {
        Text("专注 + 休息算一轮，跑满后停止", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}