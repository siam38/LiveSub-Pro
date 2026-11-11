package com.charles.livecaptionn.service

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.io.File

// Live app/device metrics shared by the dashboard and the overlay stats line.
object AppMetrics {
    fun appRamMb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024L
    }

    fun deviceFreeMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    // Total CPU ticks (user + system) used by this process, from /proc/self/stat.
    fun readCpuTicks(): Long {
        return try {
            val stat = File("/proc/self/stat").readText()
            val afterComm = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
            // afterComm[0] is field 3 (state); utime is field 14, stime is field 15.
            (afterComm[11].toLongOrNull() ?: 0L) + (afterComm[12].toLongOrNull() ?: 0L)
        } catch (_: Throwable) {
            -1L
        }
    }

    fun cpuPercent(prevTicks: Long, prevTimeMs: Long, nowTicks: Long, nowTimeMs: Long): Int {
        if (prevTicks < 0 || nowTicks < prevTicks || nowTimeMs <= prevTimeMs) return -1
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        // /proc ticks are 100 per second; normalize across all cores.
        val usedSeconds = (nowTicks - prevTicks) / 100.0
        val wallSeconds = (nowTimeMs - prevTimeMs) / 1000.0
        return (usedSeconds / (wallSeconds * cores) * 100).toInt().coerceIn(0, 100)
    }
}
