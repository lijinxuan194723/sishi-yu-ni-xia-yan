package cn.sishiyuni.core.timer

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.sishiyuni.core.data.TimerEntity

/** Android side effects are separate from persisted transitions, and replaceable in tests. */
interface TimerAlarms {
    fun exactAllowed(): Boolean
    fun schedule(timer: TimerEntity)
    fun cancel(timer: TimerEntity)
    fun dismiss(id: String)
    fun notifyFinished(timer: TimerEntity)
}

class AndroidTimerAlarms(private val context: Context, private val time: TimeSource) : TimerAlarms {
    private val manager = context.getSystemService(AlarmManager::class.java)
    override fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun pending(t: TimerEntity, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(context, t.id.hashCode(), Intent(context, TimerReceiver::class.java)
            .setAction("finish").setData(Uri.Builder().scheme("luke-timer").authority("finish")
                .appendPath(t.id).appendPath(t.generation).build())
            .putExtra("id", t.id).putExtra("generation", t.generation), flags or PendingIntent.FLAG_IMMUTABLE)

    override fun schedule(timer: TimerEntity) {
        if (timer.kind == "study" || !timer.running || timer.completed) return
        val intent = pending(timer) ?: return
        val trigger = time.elapsed() + TimerMath.remaining(timer, time)
        try {
            if (exactAllowed()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
        }
    }
    override fun cancel(timer: TimerEntity) {
        pending(timer, PendingIntent.FLAG_NO_CREATE)?.let { manager.cancel(it); it.cancel() }
    }
    override fun dismiss(id: String) { NotificationManagerCompat.from(context).cancel(id.hashCode()) }
    override fun notifyFinished(timer: TimerEntity) {
        val notifications = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!notifications.areNotificationsEnabled()) return
        val channel = NotificationChannel("luke-timers", "计时结束", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "倒计时与番茄钟到时提醒"
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("timerId", timer.id)
        }
        val content = launch?.let { PendingIntent.getActivity(context, timer.id.hashCode(), it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val text = if (timer.kind == Pomodoro.ID) "${timer.label} · ${Pomodoro.state(timer).phaseName}结束" else timer.label
        val notification = NotificationCompat.Builder(context, "luke-timers")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("夏彦提醒你")
            .setContentText(text).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(content).build()
        // The permission may be revoked between the check and the binder call.
        try { notifications.notify(timer.id.hashCode(), notification) } catch (_: SecurityException) { }
    }
}
