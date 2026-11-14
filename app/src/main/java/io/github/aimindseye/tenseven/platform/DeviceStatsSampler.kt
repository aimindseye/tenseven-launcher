// Ported from Titan AI (https://github.com/andrehafner/aiassistant,
// util/SystemStats.kt), Copyright (c) 2026 Andre Hafner, MIT License.
// See THIRD_PARTY_NOTICES.md. Trimmed for Sable Start: no
// network-type label (it needs READ_PHONE_STATE), battery from BatteryManager
// properties instead of the sticky broadcast.

package io.github.aimindseye.tenseven.platform

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10

// Stable units and physical scales used by device-status calculations.
private const val BYTES_PER_KIB = 1024.0
private const val KIB_PER_MIB = 1024.0
private const val HEAT_LOG_DIVISOR = 4.5
private const val COMPACT_KIB_THRESHOLD = 1000.0
private const val TENTH_MIB_THRESHOLD = 10_240.0
private const val MINUTES_PER_HOUR = 60f
private const val COMPACT_HOURS_LIMIT = 48f
private const val HOURS_PER_DAY = 24f
private const val SIGNAL_FULL_SCALE = 4f
private const val MIN_BATTERY_DRAW_UA = 20_000L
private const val MIN_ESTIMATED_HOURS = 0.1f
private const val MAX_ESTIMATED_HOURS = 99f
private const val BATTERY_FULL_PERCENT = 100
private const val BATTERY_FULL_SCALE = 100f
private const val MILLIS_PER_SECOND = 1000.0

/**
 * One reading for the Start header rings. Fractions are 0..1. A null value
 * means the device does not expose it (CPU on locked-down kernels, network
 * rates until a second sample exists, signal without a ready SIM).
 */
data class DeviceStats(
    val cpu: Float?,
    val ram: Float,
    /** Used fraction of internal data storage; null if unavailable. */
    val storage: Float? = null,
    val wifiUp: Long? = null,
    val wifiDown: Long? = null,
    val mobileUp: Long? = null,
    val mobileDown: Long? = null,
    val battery: Float = 0f,
    val charging: Boolean = false,
    /** Hours until empty while discharging; null while charging or unknown. */
    val hoursLeft: Float? = null,
    /** Cell signal as bars out of four; null when there is no ready SIM. */
    val signal: Float? = null,
)

/** Pure formatting for the rings, kept separate so it can be unit tested on the JVM. */
object DeviceStatsFormat {
    fun usedStorage(
        total: Long,
        available: Long,
    ): Float? =
        if (total <= 0 || available < 0) {
            null
        } else {
            (1.0 - available.coerceAtMost(total).toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    /** Throughput on a log scale: 0 below 1 KiB/s, 1 at about 30 MiB/s. */
    fun heat(bytesPerSec: Long?): Float {
        val kib = (bytesPerSec ?: 0L) / BYTES_PER_KIB
        return if (kib < 1) 0f else (log10(kib + 1) / HEAT_LOG_DIVISOR).toFloat().coerceIn(0f, 1f)
    }

    /** "0", "12K", "1.3M", "24M" per second. */
    fun rate(bytesPerSec: Long?): String {
        val bytes = bytesPerSec ?: return "–"
        val kib = bytes / BYTES_PER_KIB
        return when {
            kib < 1 -> "0"
            kib < COMPACT_KIB_THRESHOLD -> "${kib.toInt()}K"
            kib < TENTH_MIB_THRESHOLD -> String.format(Locale.US, "%.1fM", kib / KIB_PER_MIB)
            else -> "${(kib / KIB_PER_MIB).toInt()}M"
        }
    }

    /** "35m", "4h", "2d" for a time-to-empty. */
    fun hours(hours: Float?): String {
        val h = hours ?: return "–"
        return when {
            h < 1f -> "${(h * MINUTES_PER_HOUR).toInt()}m"
            h < COMPACT_HOURS_LIMIT -> "${h.toInt()}h"
            else -> "${(h / HOURS_PER_DAY).toInt()}d"
        }
    }
}

/**
 * Samples device load from on-device sources only: ActivityManager, the
 * kernel's /proc/stat or cpufreq files, TrafficStats, BatteryManager and
 * TelephonyManager. Keeps the previous counters, so call [sample] from one
 * coroutine at a time, off the main thread.
 */
class DeviceStatsSampler {
    private var lastIdle = 0L
    private var lastTotal = 0L

    private var lastNetAt = 0L
    private var lastTotalRx = -1L
    private var lastTotalTx = -1L
    private var lastMobileRx = -1L
    private var lastMobileTx = -1L

    fun sample(context: Context): DeviceStats {
        val net = network()
        val battery = battery(context)
        return DeviceStats(
            cpu = procStat() ?: cpuFreq(),
            ram = ram(context),
            storage =
                runCatching {
                    val disk = StatFs(Environment.getDataDirectory().absolutePath)
                    DeviceStatsFormat.usedStorage(disk.totalBytes, disk.availableBytes)
                }.getOrNull(),
            wifiUp = net?.get(0),
            wifiDown = net?.get(1),
            mobileUp = net?.get(2),
            mobileDown = net?.get(3),
            battery = battery.first,
            charging = battery.second,
            hoursLeft = battery.third,
            signal = signal(context),
        )
    }

    private fun ram(context: Context): Float =
        try {
            val am = context.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = info.totalMem.coerceAtLeast(1)
            val used = (info.totalMem - info.availMem).coerceIn(0, total)
            used.toFloat() / total.toFloat()
        } catch (_: RuntimeException) {
            0f
        }

    private fun signal(context: Context): Float? =
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            if (tm == null || tm.simState != TelephonyManager.SIM_STATE_READY) {
                null
            } else {
                ((tm.signalStrength?.level ?: 0) / SIGNAL_FULL_SCALE).coerceIn(0f, 1f)
            }
        } catch (_: RuntimeException) {
            null
        }

    /** Level, charging, and hours left from the charge counter and current draw. */
    private fun battery(context: Context): Triple<Float, Boolean, Float?> =
        try {
            val bm = context.getSystemService(BatteryManager::class.java)
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            val hours =
                if (charging) {
                    null
                } else {
                    val chargeUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    val draw = abs(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
                    if (chargeUah > 0 && draw > MIN_BATTERY_DRAW_UA) {
                        (chargeUah.toFloat() / draw.toFloat()).coerceIn(MIN_ESTIMATED_HOURS, MAX_ESTIMATED_HOURS)
                    } else {
                        null
                    }
                }
            Triple((level.coerceIn(0, BATTERY_FULL_PERCENT)) / BATTERY_FULL_SCALE, charging, hours)
        } catch (_: RuntimeException) {
            Triple(0f, false, null)
        }

    /**
     * Bytes per second since the previous sample as [wifiUp, wifiDown,
     * mobileUp, mobileDown]. "Wi-Fi" is everything that is not mobile.
     */
    private fun network(): List<Long?>? {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        val mrx = TrafficStats.getMobileRxBytes().coerceAtLeast(0)
        val mtx = TrafficStats.getMobileTxBytes().coerceAtLeast(0)
        val result: List<Long?> =
            if (lastNetAt > 0 && now > lastNetAt && lastTotalRx >= 0) {
                val secs = (now - lastNetAt) / MILLIS_PER_SECOND

                fun per(
                    cur: Long,
                    prev: Long,
                ) = ((cur - prev).coerceAtLeast(0) / secs).toLong()
                val mobileUp = per(mtx, lastMobileTx)
                val mobileDown = per(mrx, lastMobileRx)
                val wifiUp = (per(tx, lastTotalTx) - mobileUp).coerceAtLeast(0)
                val wifiDown = (per(rx, lastTotalRx) - mobileDown).coerceAtLeast(0)
                listOf(wifiUp, wifiDown, mobileUp, mobileDown)
            } else {
                listOf(null, null, null, null)
            }
        lastNetAt = now
        lastTotalRx = rx
        lastTotalTx = tx
        lastMobileRx = mrx
        lastMobileTx = mtx
        return result
    }

    private fun procStat(): Float? =
        try {
            val line =
                File("/proc/stat").useLines { lines ->
                    lines.firstOrNull { it.startsWith("cpu ") }
                }
            val fields =
                line
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.drop(1)
                    ?.mapNotNull { it.toLongOrNull() }
                    .orEmpty()
            if (fields.size < 4) {
                null
            } else {
                val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
                val total = fields.sum()
                val dIdle = idle - lastIdle
                val dTotal = total - lastTotal
                lastIdle = idle
                lastTotal = total
                if (dTotal <= 0) null else (1f - dIdle.toFloat() / dTotal.toFloat()).coerceIn(0f, 1f)
            }
        } catch (_: Exception) {
            null
        }

    /** Each core's current clock between its min and max, averaged. */
    private fun cpuFreq(): Float? =
        try {
            val cores =
                File("/sys/devices/system/cpu")
                    .listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
                    .orEmpty()
            var sum = 0f
            var n = 0
            for (core in cores) {
                val dir = File(core, "cpufreq")
                val cur = readLong(File(dir, "scaling_cur_freq")) ?: continue
                val max = readLong(File(dir, "cpuinfo_max_freq")) ?: continue
                val min = readLong(File(dir, "cpuinfo_min_freq")) ?: 0L
                if (max <= min) continue
                sum += ((cur - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
                n++
            }
            if (n == 0) null else sum / n
        } catch (_: Exception) {
            null
        }

    private fun readLong(file: File): Long? =
        try {
            file.readText().trim().toLongOrNull()
        } catch (_: Exception) {
            null
        }
}
