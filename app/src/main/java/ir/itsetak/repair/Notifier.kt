package ir.itsetak.repair

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

object Notifier {
    private const val CHANNEL = "setak_attention"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "اقدام مشتری", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "تأیید/رد هزینه و امتیاز مشتری"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun show(ctx: Context, count: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$count مورد نیازمند توجه")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(1001, n)
    }
}

/** Checks every ~15 minutes (Android's minimum) whether a customer approved/rejected/rated something. No Google services needed. */
class AttentionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val acc = Prefs.load(applicationContext) ?: return Result.success()
        return try {
            val res = SetakApi(acc).attention()
            val count = res.int("count")
            val last = Prefs.lastAttention(applicationContext)
            if (count > last) {
                val first = res.arr("items").objects().firstOrNull()
                val text = if (first != null) "${first.str("code")} — ${first.str("customer")} — ${first.str("label")}" else "پرونده‌ی جدیدی منتظر شماست."
                Notifier.show(applicationContext, count, text)
            }
            Prefs.setLastAttention(applicationContext, count)
            Result.success()
        } catch (e: ApiException) {
            Result.success() // wrong credentials etc.: do not retry in a loop
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
