/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.icu.util.TimeZone as IcuTimeZone
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 单次定位请求的超时上限. 设备没有定位信号时系统不会回调定位结果.
 */
private val LOCATE_TIMEOUT: Duration = 10.seconds

/**
 * 反查国家/地区的超时上限.
 */
private val GEOCODE_TIMEOUT: Duration = 5.seconds

/**
 * 可用于粗略定位的 provider, 按优先级排列. 只授予粗略定位时, 网络定位由基站与 WiFi 推算, 精度足够匹配时区.
 */
private val COARSE_PROVIDERS = listOf(
    LocationManager.NETWORK_PROVIDER,
    LocationManager.GPS_PROVIDER,
)

/**
 * 用 framework 的 [LocationManager] 取一次粗略定位, 再用 [Geocoder] 反查国家/地区, 最后匹配到 IANA 时区.
 *
 * 不依赖 Google Play Services, 定位精度与时区匹配精度都受系统提供的能力限制.
 */
private class AndroidLocationTimeZoneProvider(
    private val context: Context,
) : LocationTimeZoneProvider {
    private val logger = logger<AndroidLocationTimeZoneProvider>()

    /**
     * [Geocoder.getFromLocation] 是阻塞调用且不可中断, 只有放弃线程才能对调用方兑现超时, 因此用独立的线程池.
     */
    private val geocodeExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "ani-geocoder").apply { isDaemon = true }
    }

    override suspend fun locateTimeZone(): LocationTimeZoneResult = try {
        locateTimeZoneImpl()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        // 定位权限在请求过程中被撤销
        LocationTimeZoneResult.PermissionDenied
    } catch (e: Throwable) {
        logger.warn(e) { "Failed to locate coarse position for time zone" }
        LocationTimeZoneResult.Failed(LocationFailureReason.SystemError)
    }

    private suspend fun locateTimeZoneImpl(): LocationTimeZoneResult {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationTimeZoneResult.Failed(LocationFailureReason.NoProvider)
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return LocationTimeZoneResult.PermissionDenied
        }
        val provider = locationManager.firstEnabledProvider()
            ?: return LocationTimeZoneResult.Failed(LocationFailureReason.NoProvider)
        val location = withTimeoutOrNull(LOCATE_TIMEOUT) {
            locationManager.awaitSingleLocation(provider)
        } ?: return LocationTimeZoneResult.Failed(LocationFailureReason.Timeout)

        val zoneId = withContext(Dispatchers.IO) { resolveZoneId(location.latitude, location.longitude) }
        return zoneId?.let { LocationTimeZoneResult.Success(it) } ?: LocationTimeZoneResult.ZoneNotFound
    }

    /**
     * 由坐标得到 IANA 时区 ID.
     *
     * 先反查出坐标所在的国家/地区, 再在该国家/地区的规范时区中选择:
     * 1. 设备的当前时区属于该国家/地区时取设备当前时区;
     * 2. 否则取 UTC 偏移与经度推算值最近的规范时区.
     *
     * 坐标本身不含行政区划, 因此多时区国家内的结果是近似值, 由设置页展示给用户确认.
     */
    private fun resolveZoneId(latitude: Double, longitude: Double): String? {
        val candidates = canonicalZonesOfRegion(geocodeRegion(latitude, longitude))
        if (candidates.isEmpty()) return null
        deviceZoneIdIn(candidates)?.let { return it }
        val estimatedOffsetMillis = (longitude / HOURS_PER_ZONE * MILLIS_PER_HOUR).toInt()
        return candidates.minByOrNull { abs(it.rawOffset - estimatedOffsetMillis) }?.id
    }

    /**
     * 设备当前时区, 仅当它属于 [candidates] 时返回.
     */
    private fun deviceZoneIdIn(candidates: List<IcuTimeZone>): String? {
        val deviceZoneId = currentSystemTimeZone().id
        val canonicalDeviceZoneId = runCatching { IcuTimeZone.getCanonicalID(deviceZoneId) }.getOrNull()
        return candidates.firstOrNull { it.id == deviceZoneId || it.id == canonicalDeviceZoneId }?.id
    }

    /**
     * 在 [GEOCODE_TIMEOUT] 内反查坐标所在的国家/地区代码, 取不到时返回 null.
     */
    private fun geocodeRegion(latitude: Double, longitude: Double): String? {
        val future = geocodeExecutor.submit(Callable { geocodeRegionBlocking(latitude, longitude) })
        return try {
            future.get(GEOCODE_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: ExecutionException) {
            null
        } catch (e: InterruptedException) {
            future.cancel(true)
            null
        }
    }

    @Suppress("DEPRECATION") // 同步版本在 API 33 起标记为废弃, 异步版本只在 API 33 及以上存在
    private fun geocodeRegionBlocking(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address> = runCatching {
            geocoder.getFromLocation(latitude, longitude, 1)
        }.getOrNull() ?: return null
        return addresses.firstOrNull()?.countryCode
    }

    private fun canonicalZonesOfRegion(region: String?): List<IcuTimeZone> {
        if (region.isNullOrBlank()) return emptyList()
        val ids = runCatching {
            IcuTimeZone.getAvailableIDs(
                IcuTimeZone.SystemTimeZoneType.CANONICAL_LOCATION,
                region.uppercase(Locale.ROOT),
                null,
            )
        }.getOrNull() ?: return emptyList()
        return ids.map { IcuTimeZone.getTimeZone(it) }
    }

    private fun LocationManager.firstEnabledProvider(): String? = COARSE_PROVIDERS.firstOrNull { provider ->
        runCatching { isProviderEnabled(provider) }.getOrDefault(false)
    }

    /**
     * 请求一次定位. 系统回调第一个结果后解除监听; 协程被取消 (含超时) 时同样解除监听.
     */
    @SuppressLint("MissingPermission") // 调用前已检查 ACCESS_COARSE_LOCATION
    @Suppress("DEPRECATION") // 单次定位的替代 API (getCurrentLocation) 只在 API 30 及以上存在, 且仍需自行超时
    private suspend fun LocationManager.awaitSingleLocation(provider: String): Location =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
            }
            continuation.invokeOnCancellation { removeUpdates(listener) }
            requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }

    private companion object {
        /**
         * 经度每 15 度对应一个时区.
         */
        const val HOURS_PER_ZONE = 15.0

        const val MILLIS_PER_HOUR = 3_600_000
    }
}

actual fun createLocationTimeZoneProvider(context: Context): LocationTimeZoneProvider {
    return AndroidLocationTimeZoneProvider(context)
}
