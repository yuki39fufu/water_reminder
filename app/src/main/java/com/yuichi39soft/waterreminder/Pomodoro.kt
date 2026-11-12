package com.yuichi39soft.waterreminder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlin.math.abs
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
    /** 跑完全部轮次后置真，UI 用它显示"已完成"，避免和全新状态混在一起 */
    var completed by mutableStateOf(prefs.getBoolean(KEY_POM_COMPLETED, false))
        private set

    /** 每次点复原自增；UI 用它区分"复原"和正常走动 */
    var resetSignal by mutableIntStateOf(0)
        private set

    /** 最后结束的那个阶段属于第几轮 / 共几轮 / 是否跑完全部（通知文案用） */
    var lastFinishedRound by mutableIntStateOf(1)
        private set
    var lastFinishedRounds by mutableIntStateOf(4)
        private set
    var lastFinishedAllDone by mutableStateOf(false)
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
        completed = prefs.getBoolean(KEY_POM_COMPLETED, false)
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
            // 暂停状态也要恢复进度，否则旋转屏幕/进程回收后会跳回整段时长
            remainingMillis = prefs.getLong(KEY_POM_REMAINING, phaseMs()).coerceIn(0L, phaseMs())
        }
    }

    /** 回到前台时把内存状态和 prefs 对齐（接收器可能推进过阶段） */
    fun refreshFromStorage() {
        syncFromPrefs()
    }

    /** 闹钟接收器可能已经推进过状态，这里把内存状态同步过来 */
    private fun syncFromPrefs() {
        running = prefs.getBoolean(KEY_POM_RUNNING, false)
        completed = prefs.getBoolean(KEY_POM_COMPLETED, false)
        phase = loadPhase()
        currentRound = prefs.getInt(KEY_POM_ROUND, 1)
        endAt = prefs.getLong(KEY_POM_END_AT, 0L)
        remainingMillis = if (running && endAt > 0L) {
            (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            prefs.getLong(KEY_POM_REMAINING, phaseMs()).coerceIn(0L, phaseMs())
        }
    }

    /** 从 endTime 这个时间点推进到下一个阶段 */
    private fun advanceFrom(endTime: Long) {
        if (phase == PomodoroPhase.FOCUS) {
            if (currentRound >= rounds) {
                running = false
                completed = true
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
            .putLong(KEY_POM_REMAINING, remainingMillis)
            .putBoolean(KEY_POM_COMPLETED, completed)
            .apply()
    }

    private fun scheduleAlarm() {
        if (running) schedulePomodoroAlarm(appContext, endAt)
    }

    private fun cancelAlarm() {
        cancelPomodoroAlarm(appContext)
    }

    fun updateFocusMinutes(value: Int) {
        focusMinutes = value.coerceIn(1, 180)
        prefs.edit().putInt(KEY_POM_FOCUS, focusMinutes).apply()
        if (phase == PomodoroPhase.FOCUS) retimeCurrentPhase()
    }

    fun updateBreakMinutes(value: Int) {
        breakMinutes = value.coerceIn(1, 60)
        prefs.edit().putInt(KEY_POM_BREAK, breakMinutes).apply()
        if (phase == PomodoroPhase.BREAK) retimeCurrentPhase()
    }

    /** 改了当前阶段时长：倒计时按新时长重开，避免和环形进度对不上 */
    private fun retimeCurrentPhase() {
        syncFromPrefs()
        remainingMillis = phaseMs()
        if (running) {
            endAt = System.currentTimeMillis() + remainingMillis
            persist()
            scheduleAlarm()
        } else {
            persist()
        }
    }

    fun updateRounds(value: Int) {
        rounds = value.coerceIn(1, 12)
        if (currentRound > rounds) currentRound = rounds
        prefs.edit().putInt(KEY_POM_ROUNDS, rounds).apply()
        persist()
    }

    fun updateRingEnabled(enabled: Boolean) {
        ringEnabled = enabled
        prefs.edit().putBoolean(KEY_POM_RING, enabled).apply()
    }

    fun toggle() {
        if (running) pause() else start()
    }

    fun start() {
        // 闹钟接收器可能刚推进过阶段，先对齐再启动
        syncFromPrefs()
        if (running) return
        completed = false
        if (remainingMillis <= 0L) remainingMillis = phaseMs()
        endAt = System.currentTimeMillis() + remainingMillis
        running = true
        persist()
        scheduleAlarm()
    }

    fun pause() {
        // 同上：别把接收器刚推进的阶段用旧 endAt 覆盖回去
        syncFromPrefs()
        if (!running) return
        remainingMillis = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        running = false
        persist()
        cancelAlarm()
    }

    fun reset() {
        running = false
        completed = false
        resetSignal++
        phase = PomodoroPhase.FOCUS
        currentRound = 1
        remainingMillis = focusMs()
        endAt = 0L
        persist()
        cancelAlarm()
    }

    /** 暂停在非初始状态：此时才需要显示重置按钮 */
    fun isMidway(): Boolean {
        if (running) return false
        return currentRound > 1 || phase != PomodoroPhase.FOCUS || remainingMillis < phaseMs() - 500L
    }

    /** 返回刚刚结束的阶段（若有），由调用方负责响铃 */
    fun tick(now: Long): PomodoroPhase? {
        if (!running) return null
        // 闹钟接收器可能已经推进过状态（它在 endAt 那一刻先响），这里同步而不是再推进一次
        val prefEnd = prefs.getLong(KEY_POM_END_AT, 0L)
        val prefRunning = prefs.getBoolean(KEY_POM_RUNNING, false)
        if (prefRunning != running || prefEnd != endAt) {
            syncFromPrefs()
            return null
        }
        if (now < endAt) {
            remainingMillis = endAt - now
            return null
        }
        // 休眠/后台很久时一次性快进到当前阶段，只为最后结束的那个阶段提醒一次
        var lastFinished: PomodoroPhase? = null
        var lastEndedAt = 0L
        var guard = 0
        while (running && endAt > 0L && endAt <= now && guard++ < 64) {
            lastEndedAt = endAt
            lastFinished = phase
            lastFinishedRound = currentRound
            advanceFrom(endAt)
        }
        lastFinishedRounds = rounds
        lastFinishedAllDone = completed && !running
        if (guard >= 64 && running && endAt <= now) {
            // 追平次数超限（异常数据）：直接停下，避免 00:00 还在"运行"
            running = false
            remainingMillis = phaseMs()
        }
        remainingMillis = if (running) (endAt - now).coerceAtLeast(0L) else phaseMs()
        persist()
        // 标记最后这个结束点，避免同一时刻的闹钟再弹一次
        if (lastEndedAt > 0L) prefs.edit().putLong(KEY_POM_FIRED_AT, lastEndedAt).apply()
        if (running) scheduleAlarm() else cancelAlarm()
        return lastFinished
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

/** 重新排番茄钟的阶段结束闹钟（供 ViewModel 与开机广播复用） */
internal fun schedulePomodoroAlarm(context: Context, endAt: Long) {
    if (endAt <= 0L) return
    val intent = Intent(context, PomodoroAlarmReceiver::class.java).apply {
        action = ACTION_POMODORO_ALARM
    }
    val pendingIntent = android.app.PendingIntent.getBroadcast(
        context,
        POMODORO_REQUEST_CODE,
        intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )
    // 和饮水提醒用同一套调度：优先 setAlarmClock（进程被杀/Doze 下也能准点送达），
    // 否则应用被清掉后闹钟会被系统延后几十秒，这段时间里用户收不到通知。
    AlarmScheduler.setAlarm(context, endAt, pendingIntent)
}

internal fun cancelPomodoroAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
    val intent = Intent(context, PomodoroAlarmReceiver::class.java).apply {
        action = ACTION_POMODORO_ALARM
    }
    val pendingIntent = android.app.PendingIntent.getBroadcast(
        context,
        POMODORO_REQUEST_CODE,
        intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
    )
    if (pendingIntent != null) alarmManager.cancel(pendingIntent)
}

/** 阶段结束的闹钟：只负责响铃/通知，阶段推进由应用内的状态机负责 */
class PomodoroAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 升级后闹钟可能先于应用启动触发，先把通道建好，否则通知会被系统丢掉
        ensureNotificationChannels(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endAt = prefs.getLong(KEY_POM_END_AT, 0L)
        if (endAt <= 0L) return
        // 应用内已经推进到下一阶段时，endAt 已经被推后，这个"旧闹钟"不该再提醒
        if (System.currentTimeMillis() + 1500L < endAt) return
        // 同一个结束点只响一次（应用内 tick 与闹钟可能同时到达）
        val fired = prefs.getLong(KEY_POM_FIRED_AT, 0L)
        if (fired == endAt) return
        prefs.edit().putLong(KEY_POM_FIRED_AT, endAt).apply()

        val phaseText = prefs.getString(KEY_POM_PHASE, "FOCUS")
        val endedPhase = if (phaseText == PomodoroPhase.BREAK.name) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
        val endedRound = prefs.getInt(KEY_POM_ROUND, 1)
        val endedRounds = prefs.getInt(KEY_POM_ROUNDS, 4)

        // 进程可能已经被杀：自己推进状态并排下一阶段。
        // 闹钟被 Doze / ROM 省电延迟时可能已经跳过好几个阶段，
        // 这里一次快进到未来，只对最先到点的那个阶段提醒一次。
        val now = System.currentTimeMillis()
        var nextEnd = endAt
        var steps = 0
        // 快进时可能跨过多个阶段：文案要指向"最后结束"的那个，而不是最先的
        var notifyPhase = endedPhase
        var notifyRound = endedRound
        while (prefs.getBoolean(KEY_POM_RUNNING, false) && nextEnd > 0L && nextEnd <= now && steps++ < 64) {
            val cur = prefs.getString(KEY_POM_PHASE, "FOCUS")
            notifyPhase = if (cur == PomodoroPhase.BREAK.name) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
            notifyRound = prefs.getInt(KEY_POM_ROUND, 1)
            advancePomodoroPrefs(prefs, nextEnd)
            nextEnd = prefs.getLong(KEY_POM_END_AT, 0L)
        }
        val stillRunning = prefs.getBoolean(KEY_POM_RUNNING, false)
        val allDone = !stillRunning && prefs.getBoolean(KEY_POM_COMPLETED, false)

        if (prefs.getBoolean(KEY_POM_RING, true)) {
            playPhaseChime(context)
        }
        notifyPhaseFinished(context, notifyPhase, notifyRound, endedRounds, allDone)

        if (stillRunning) {
            schedulePomodoroAlarm(context, prefs.getLong(KEY_POM_END_AT, 0L))
        }
    }
}

/**
 * 阶段结束后推进 SharedPreferences 中的番茄钟状态。
 * 供闹钟接收器使用（应用进程不在时也要能继续往下走）。
 */
internal fun advancePomodoroPrefs(
    prefs: android.content.SharedPreferences,
    fromEndAt: Long
) {
    val phase = if (prefs.getString(KEY_POM_PHASE, "FOCUS") == PomodoroPhase.BREAK.name) {
        PomodoroPhase.BREAK
    } else {
        PomodoroPhase.FOCUS
    }
    val round = prefs.getInt(KEY_POM_ROUND, 1)
    val rounds = prefs.getInt(KEY_POM_ROUNDS, 4)
    val focusMs = prefs.getInt(KEY_POM_FOCUS, 25) * 60_000L
    val breakMs = prefs.getInt(KEY_POM_BREAK, 5) * 60_000L
    val edit = prefs.edit()
    if (phase == PomodoroPhase.FOCUS) {
        if (round >= rounds) {
            edit.putBoolean(KEY_POM_RUNNING, false)
                .putBoolean(KEY_POM_COMPLETED, true)
                .putString(KEY_POM_PHASE, PomodoroPhase.FOCUS.name)
                .putInt(KEY_POM_ROUND, 1)
                .putLong(KEY_POM_END_AT, 0L)
                .putLong(KEY_POM_REMAINING, focusMs)
        } else {
            edit.putString(KEY_POM_PHASE, PomodoroPhase.BREAK.name)
                .putLong(KEY_POM_END_AT, fromEndAt + breakMs)
        }
    } else {
        edit.putString(KEY_POM_PHASE, PomodoroPhase.FOCUS.name)
            .putInt(KEY_POM_ROUND, (round + 1).coerceAtMost(rounds))
            .putLong(KEY_POM_END_AT, fromEndAt + focusMs)
    }
    edit.apply()
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
    var showFocusDialog by rememberSaveable { mutableStateOf(false) }
    var showBreakDialog by rememberSaveable { mutableStateOf(false) }
    var showRoundDialog by rememberSaveable { mutableStateOf(false) }

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
            val targetConsumed = pomodoro.roundConsumedFraction()
            val consumedAnim = remember { Animatable(targetConsumed) }
            var handledReset by remember { mutableIntStateOf(pomodoro.resetSignal) }
            LaunchedEffect(targetConsumed, pomodoro.resetSignal) {
                // 复原用显式信号触发：哪怕只跑了 2 秒、消耗不到 1%，也照样播回退动画。
                val justReset = pomodoro.resetSignal != handledReset
                if (justReset) handledReset = pomodoro.resetSignal
                val delta = abs(targetConsumed - consumedAnim.value)
                if (justReset || delta > 0.05f) {
                    consumedAnim.animateTo(
                        targetValue = targetConsumed,
                        animationSpec = tween(
                            durationMillis = 620,
                            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                        )
                    )
                } else {
                    // 正常走时每次只挪一点点（200ms 一跳）：直接跟住，别让环一直滞后
                    consumedAnim.snapTo(targetConsumed)
                }
            }
            PomodoroRing(
                focusFractionOfRound = pomodoro.focusMinutes.toFloat() /
                    (pomodoro.focusMinutes + pomodoro.breakMinutes).toFloat(),
                consumedFraction = consumedAnim.value,
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
                        pomodoro.completed -> "已完成"
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

        // 分界线：专注/休息交界一条，12 点方向（专注起点 = 休息终点）再一条
        val inner = radius - stroke / 2f
        val outer = radius + stroke / 2f
        fun drawBoundary(angleDeg: Float) {
            val angle = Math.toRadians(angleDeg.toDouble())
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
        if (focusAngle > 0.5f && focusAngle < 359.5f) {
            drawBoundary(-90f + focusAngle)
        }
        // 12 点方向：两段颜色在这里首尾相接，也要一条线
        drawBoundary(-90f)
    }
}

/** 播放 / 暂停图标（纯代码绘制，便于做切换动画） */
@Composable
private fun PlayPauseGlyph(running: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        if (running) {
            val barWidth = size.width * 0.26f
            val gap = size.width * 0.18f
            val top = size.height * 0.16f
            val barHeight = size.height * 0.68f
            val radius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width / 2f - gap / 2f - barWidth, top),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
            drawRoundRect(
                color = color,
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
            drawPath(path, color)
        }
    }
}

/** 开始 / 暂停按钮（悬浮层里，位于设置按钮上方） */
@Composable
fun StartPauseButton(
    running: Boolean,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = containerColor
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = running,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(170)) +
                    androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(220),
                        initialScale = 0.55f
                    )) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                        androidx.compose.animation.scaleOut(
                            animationSpec = androidx.compose.animation.core.tween(160),
                            targetScale = 0.55f
                        ))
            },
            label = "play-pause"
        ) { isRunning ->
            PlayPauseGlyph(isRunning, iconColor)
        }
    }
}

/** 重置按钮：暂停在非初始状态时出现在开始/暂停按钮上方 */
@Composable
fun ResetButton(
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = containerColor
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "重置",
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
/** 阶段结束响铃（系统默认通知音） */
fun playPhaseChime(context: Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    }
}

/** 阶段结束通知：点击进入应用 */
fun notifyPhaseFinished(
    context: Context,
    finished: PomodoroPhase,
    finishedRound: Int = -1,
    finishedRounds: Int = -1,
    allDone: Boolean = false
) {
    if (!hasNotificationPermission(context)) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val round = if (finishedRound > 0) finishedRound else prefs.getInt(KEY_POM_ROUND, 1)
    val rounds = if (finishedRounds > 0) finishedRounds else prefs.getInt(KEY_POM_ROUNDS, 4)
    val text = when {
        allDone -> "全部轮次完成，休息一下吧 🎉"
        finished == PomodoroPhase.FOCUS -> "专注结束，休息一下（第 $round / $rounds 轮）"
        else -> "休息结束，继续专注（第 $round / $rounds 轮）"
    }

    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_SCREEN, "pomodoro")
    }
    val contentIntent = android.app.PendingIntent.getActivity(
        context,
        8101,
        openIntent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_POMODORO)
        .setSmallIcon(android.R.drawable.ic_menu_recent_history)
        .setContentTitle("番茄钟")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()
    runCatching {
        NotificationManagerCompat.from(context).notify(2002, notification)
    }
}

/** 番茄钟设置 */
@Composable
fun PomodoroSettingsContent(pomodoro: PomodoroViewModel) {
    var showFocusDialog by rememberSaveable { mutableStateOf(false) }
    var showBreakDialog by rememberSaveable { mutableStateOf(false) }
    var showRoundDialog by rememberSaveable { mutableStateOf(false) }

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