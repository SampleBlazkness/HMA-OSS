package org.frknkrc44.hma_oss.zygote.service

import android.os.Handler
import android.os.Looper
import org.frknkrc44.hma_oss.zygote.util.Logcat.logI
import org.frknkrc44.hma_oss.zygote.util.Logcat.logW
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Writes HMA's hide pairs straight into the framework's own
 * AppsFilterImpl.mShouldFilterCache, so package visibility filtering is done by
 * the system instead of by a per-query Xposed hook (which is measurable).
 *
 * Only used when the cache is present and ready; otherwise the legacy hooks
 * keep doing the work.
 */
object AppsFilterCache {
    private const val TAG = "HMA-AppsFilterCache"
    private const val PACKAGES_LIST = "/data/system/packages.list"
    private const val DEBOUNCE_MS = 1_500L

    @Volatile private var filter: Any? = null
    @Volatile private var matrix: Any? = null
    @Volatile private var putMethod: Method? = null
    @Volatile private var cacheReadyField: Field? = null

    @Volatile
    var active: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false

    @Volatile private var running = false

    fun attach(impl: Any?) {
        if (impl == null || matrix != null) return
        try {
            // shouldFilterApplication is dispatched on a *snapshot* copy; only the
            // live AppsFilterImpl instance owns the matrix the framework rebuilds
            // snapshots from, so resolve that one.
            val live = if (impl.javaClass.name == "com.android.server.pm.AppsFilterImpl") {
                impl
            } else {
                findLiveFilter() ?: return
            }
            val mf = findField(live.javaClass, "mShouldFilterCache") ?: return
            mf.isAccessible = true
            val m = mf.get(live) ?: return
            val pm = m.javaClass.methods.firstOrNull {
                it.name == "put" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == Boolean::class.javaPrimitiveType
            } ?: return
            pm.isAccessible = true
            filter = live
            matrix = m
            putMethod = pm
            cacheReadyField = findField(live.javaClass, "mCacheReady")
            logI(TAG) { "attached to ${live.javaClass.name}, matrix=${m.javaClass.name}" }
            UserService.service?.let { schedule(it) }
        } catch (t: Throwable) {
            logW(TAG) { "attach failed: $t" }
        }
    }

    private fun findLiveFilter(): Any? {
        try {
            val localServices = Class.forName("com.android.server.LocalServices")
            val getService = localServices.getMethod("getService", Class::class.java)
            val pmInternal = getService.invoke(
                null,
                Class.forName("com.android.server.pm.PackageManagerInternal"),
            ) ?: return null
            var pms: Any? = null
            for (f in pmInternal.javaClass.declaredFields) {
                if (f.name == "this\$0" || f.type.name == "com.android.server.pm.PackageManagerService") {
                    f.isAccessible = true
                    pms = f.get(pmInternal)
                    if (pms != null) break
                }
            }
            if (pms == null) return null
            val af = findField(pms.javaClass, "mAppsFilter") ?: return null
            af.isAccessible = true
            return af.get(pms)
        } catch (t: Throwable) {
            logW(TAG) { "resolve live AppsFilter failed: $t" }
            return null
        }
    }

    fun schedule(service: HMAService) {
        if (matrix == null || scheduled) return
        scheduled = true
        handler.postDelayed({
            scheduled = false
            applyAsync(service)
        }, DEBOUNCE_MS)
    }

    private fun applyAsync(service: HMAService) {
        if (running) return
        running = true
        Thread({
            try {
                apply(service)
            } catch (t: Throwable) {
                logW(TAG) { "apply failed: $t" }
            } finally {
                running = false
            }
        }, "hma-appsfilter").start()
    }

    private fun apply(service: HMAService) {
        val m = matrix ?: return
        val pm = putMethod ?: return

        cacheReadyField?.let {
            it.isAccessible = true
            if (it.getBoolean(filter) != true) {
                logW(TAG) { "shouldFilter cache is not ready, retrying" }
                handler.postDelayed({ applyAsync(service) }, 5_000L)
                return
            }
        }

        val uids = readPackageUids()
        val cfg = service.config
        var count = 0

        for (caller in cfg.scope.keys) {
            val callerUid = uids[caller] ?: continue
            for ((target, targetUid) in uids) {
                if (target == caller) continue
                if (service.shouldHide(caller, target, 0)) {
                    pm.invoke(m, callerUid, targetUid, true)
                    count++
                }
            }
        }

        active = count > 0
        logI(TAG) { "applied $count hide pairs (active=$active)" }
    }

    private fun readPackageUids(): Map<String, Int> {
        val map = HashMap<String, Int>(512)
        try {
            File(PACKAGES_LIST).forEachLine { line ->
                val parts = line.split(' ')
                if (parts.size >= 2) parts[1].toIntOrNull()?.let { map[parts[0]] = it }
            }
        } catch (t: Throwable) {
            logW(TAG) { "read $PACKAGES_LIST failed: $t" }
        }
        return map
    }

    private fun findField(cls: Class<*>, name: String): Field? {
        var c: Class<*>? = cls
        while (c != null) {
            try {
                return c.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
            }
            c = c.superclass
        }
        return null
    }
}
