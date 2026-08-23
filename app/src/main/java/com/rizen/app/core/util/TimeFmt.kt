package com.rizen.app.core.util

import com.rizen.app.core.i18n.Strings
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFmt {

    fun clock(hour: Int, minute: Int, use24h: Boolean): String =
        if (use24h) String.format(Locale.US, "%02d:%02d", hour, minute)
        else {
            val h = when {
                hour % 12 == 0 -> 12
                else -> hour % 12
            }
            String.format(Locale.US, "%d:%02d", h, minute)
        }

    fun meridiem(hour: Int): String = if (hour < 12) "AM" else "PM"

    fun clockOf(epochMs: Long, use24h: Boolean): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        return clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), use24h)
    }

    fun clockWithSeconds(epochMs: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        return String.format(
            Locale.US, "%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
    }

    /** "07:14" style countdown for timers. */
    fun mmss(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    fun hhmmss(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val h = total / 3600
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, (total % 3600) / 60, total % 60)
        else String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    /** "7h 12m" / "45m" / "30s" — for "rings in …". */
    fun humanDuration(ms: Long, s: Strings): String {
        val d = ms.coerceAtLeast(0)
        val h = TimeUnit.MILLISECONDS.toHours(d)
        val m = TimeUnit.MILLISECONDS.toMinutes(d) % 60
        val sec = TimeUnit.MILLISECONDS.toSeconds(d) % 60
        return when {
            h > 0 -> "${h}${s.hoursShort} ${m}${s.minutesShort}"
            m > 0 -> "${m}${s.minutesShort}"
            else -> "${sec}${s.secondsShort}"
        }
    }

    fun startOfDay(epochMs: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun endOfDay(epochMs: Long = System.currentTimeMillis()): Long =
        startOfDay(epochMs) + TimeUnit.DAYS.toMillis(1) - 1

    fun atTimeToday(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun hourOf(epochMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)

    fun minuteOf(epochMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.MINUTE)

    /** Fraction of the day [0,1) — drives the 24h radial timeline in stats. */
    fun dayFraction(epochMs: Long): Float {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        val secs = c.get(Calendar.HOUR_OF_DAY) * 3600 + c.get(Calendar.MINUTE) * 60 +
            c.get(Calendar.SECOND)
        return secs / 86400f
    }

    fun dayKey(epochMs: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }
}

/**
 * Day bitmask helpers. Bit 0 = Monday … bit 6 = Sunday.
 * Mask 0 means "one-shot": fires at the next occurrence then disables itself.
 */
object DayMask {
    const val NONE = 0
    const val WEEKDAYS = 0b0011111
    const val WEEKENDS = 0b1100000
    const val EVERY_DAY = 0b1111111

    fun has(mask: Int, dayIndex: Int) = (mask shr dayIndex) and 1 == 1
    fun toggle(mask: Int, dayIndex: Int) = mask xor (1 shl dayIndex)

    /** Calendar.DAY_OF_WEEK (Sun=1) -> our index (Mon=0). */
    fun indexFromCalendar(calDay: Int) = (calDay + 5) % 7

    val labelsEn = listOf("M", "T", "W", "T", "F", "S", "S")
    val labelsFa = listOf("د", "س", "چ", "پ", "ج", "ش", "ی")
}
