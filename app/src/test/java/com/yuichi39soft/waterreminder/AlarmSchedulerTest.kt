package com.yuichi39soft.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 验证闹钟“下一次触发时间”的推算：必须在未来，且不超过 24 小时，
 * 并保留设置的时/分。这是提醒能逐日续期的核心逻辑。
 */
class AlarmSchedulerTest {

    @Test
    fun nextTriggerIsInTheFutureAndWithinOneDay() {
        val now = System.currentTimeMillis()
        for (time in listOf("00:00", "09:00", "12:34", "23:59", "7:05")) {
            val trigger = AlarmScheduler.nextTriggerMillis(time)
            assertTrue("$time 的触发时间应在未来", trigger > now)
            assertTrue(
                "$time 的触发时间不应超过 24 小时",
                trigger - now <= 24 * 60 * 60 * 1000L + 60_000L
            )
        }
    }

    @Test
    fun nextTriggerKeepsHourAndMinute() {
        val trigger = AlarmScheduler.nextTriggerMillis("07:45")
        val calendar = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(7, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }

    @Test
    fun nextTriggerRollsToTomorrowWhenTimeAlreadyPassed() {
        val now = Calendar.getInstance()
        // 取一个已经过去一分钟的时间点，触发时间必须落在明天
        val past = Calendar.getInstance().apply { add(Calendar.MINUTE, -1) }
        val time = String.format("%02d:%02d", past.get(Calendar.HOUR_OF_DAY), past.get(Calendar.MINUTE))

        val trigger = AlarmScheduler.nextTriggerMillis(time)
        val triggerCalendar = Calendar.getInstance().apply { timeInMillis = trigger }

        assertTrue("已过去的时间点应排到下一次", trigger > now.timeInMillis)
        val expected = Calendar.getInstance().apply {
            add(Calendar.MINUTE, -1)
            add(Calendar.DATE, 1)
        }
        assertEquals(expected.get(Calendar.DAY_OF_YEAR), triggerCalendar.get(Calendar.DAY_OF_YEAR))
    }
}
