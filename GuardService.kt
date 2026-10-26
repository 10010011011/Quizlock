package com.example.quizlock

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class GuardService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private val warned = mutableSetOf<String>()
    private var last = 0L
    private val loop = object : Runnable { override fun run() { tick(); h.postDelayed(this, 10_000) } }

    override fun onServiceConnected() { h.post(loop) }
    override fun onInterrupt() {}
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) e.packageName?.toString()?.let { guard(it) }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        for (p in Store.unlockedPkgs(this)) {
            val e = Store.expiry(this, p)
            if (e == Long.MAX_VALUE) continue
            if (now >= e) Store.relock(this, p)
            else if (e - now <= 600_000 && warned.add(p)) warn(p)
        }
        warned.retainAll(Store.unlockedPkgs(this).toSet())
        rootInActiveWindow?.packageName?.toString()?.let { guard(it) }
    }

    private fun guard(pkg: String) {
        if (pkg == packageName || pkg !in Store.locked(this) || Store.isUnlocked(this, pkg)) return
        val n = System.currentTimeMillis()
        if (n - last < 1500) return
        last = n
        startActivity(Intent(this, LockActivity::class.java).putExtra("pkg", pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    private fun warn(p: String) {
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }
        val msg = "$label locks again in 10 minutes"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("w", "Lock warnings", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(p.hashCode(), Notification.Builder(this, "w").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(msg).setAutoCancel(true).build())
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
